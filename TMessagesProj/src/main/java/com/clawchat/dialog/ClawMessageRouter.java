package com.clawchat.dialog;

import com.clawchat.gateway.GatewayClient;
import com.clawchat.gateway.model.GatewayEvent;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Orchestrator that routes chat events between {@link GatewayClient} and
 * {@link ClawMessageStoreImpl}.
 *
 * <p>Incoming {@code chat.delta} / {@code chat.complete} events are looked up
 * by {@code sessionKey}, resolved to a virtual dialog id through
 * {@link ClawDialogBridge}, then dispatched into the store. Unknown events are
 * silently ignored.
 *
 * <p>Outgoing: {@link #sendText(long, String)} adds the user message, begins
 * an assistant placeholder in STREAMING state, and forwards the request to the
 * gateway. Ack failures flip the placeholder to ERROR.
 *
 * <p>ASSUMPTION — {@code chat.delta} payload shape is
 * {@code {"sessionKey":"...","messageId":"...","chunk":"..."}}. If the real
 * protocol uses different field names this class is the single place to
 * adjust.
 */
public final class ClawMessageRouter {

    private final GatewayClient gatewayClient;
    private final ClawDialogBridge bridge;
    private final ClawMessageStoreImpl store;

    private final GatewayClient.EventListener eventListener = new GatewayClient.EventListener() {
        @Override
        public void onEvent(GatewayEvent event) {
            handleEvent(event);
        }
    };

    public ClawMessageRouter(GatewayClient gatewayClient, ClawDialogBridge bridge, ClawMessageStore store) {
        if (!(store instanceof ClawMessageStoreImpl)) {
            throw new IllegalArgumentException("ClawMessageRouter requires ClawMessageStoreImpl");
        }
        this.gatewayClient = gatewayClient;
        this.bridge = bridge;
        this.store = (ClawMessageStoreImpl) store;
        gatewayClient.addEventListener(eventListener);
    }

    /** Detach listeners. Call from shutdown. */
    public void dispose() {
        gatewayClient.removeEventListener(eventListener);
    }

    private void handleEvent(GatewayEvent event) {
        if (event == null || event.event == null || event.payload == null) return;
        String type = event.event;
        JSONObject p = event.payload;
        String sessionKey = p.optString("sessionKey", null);
        if (sessionKey == null) return;

        // Resolve sessionKey -> dialogId (allocate if necessary so late-arriving
        // events for a known session still land in the right bucket).
        long dialogId = bridge.allocateDialogId(sessionKey);

        if ("chat.delta".equals(type)) {
            String messageId = p.optString("messageId", null);
            String chunk = p.optString("chunk", "");
            if (messageId == null) return;
            store.appendDelta(dialogId, messageId, chunk);
        } else if ("chat.complete".equals(type)) {
            String messageId = p.optString("messageId", null);
            if (messageId == null) return;
            store.completeAssistant(dialogId, messageId);
        }
        // else: ignore unknown events silently.
    }

    /**
     * Send a user text to the given ClawChat dialog. Immediately shows the
     * user's message and an empty assistant STREAMING placeholder so the UI
     * can paint right away.
     */
    public void sendText(final long dialogId, String text) {
        final String sessionKey = bridge.sessionKeyFor(dialogId);
        if (sessionKey == null) {
            throw new IllegalArgumentException("Not a ClawChat dialog: " + dialogId);
        }
        store.addUserMessage(dialogId, sessionKey, text);

        final String assistantId = "a-" + UUID.randomUUID().toString();
        store.beginAssistant(dialogId, sessionKey, assistantId);

        gatewayClient.chatSend(sessionKey, text, new GatewayClient.AckCallback() {
            @Override
            public void onAck(boolean ok, String errorOrNull) {
                if (!ok) {
                    store.errorAssistant(dialogId, assistantId);
                }
            }
        });
    }

    /** Cancel an in-flight streaming response on the given ClawChat dialog. */
    public void cancel(long dialogId) {
        String sessionKey = bridge.sessionKeyFor(dialogId);
        if (sessionKey == null) return;
        gatewayClient.chatCancel(sessionKey);
    }
}
