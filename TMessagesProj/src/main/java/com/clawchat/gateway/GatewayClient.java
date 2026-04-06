package com.clawchat.gateway;

import com.clawchat.gateway.model.ConnectionState;
import com.clawchat.gateway.model.GatewayEvent;

/**
 * Contract for OpenClaw gateway WebSocket communication.
 *
 * <p>Owned by Agent A (feat/gateway). This interface is READ-ONLY for all other agents.
 * Implementation: {@code OkHttpGatewayClient}.
 *
 * <p>Protocol: OpenClaw WS v3 — {@code req}/{@code res}/{@code event} frames over JSON.
 */
public interface GatewayClient {

    /** Connect to the gateway. Idempotent. */
    void connect();

    /** Disconnect and stop reconnection. */
    void disconnect();

    /** Current connection state (cold-start returns {@link ConnectionState#DISCONNECTED}). */
    ConnectionState getConnectionState();

    /** Register a connection state listener. */
    void addConnectionStateListener(ConnectionStateListener l);
    void removeConnectionStateListener(ConnectionStateListener l);

    /** Register an event listener (all incoming server events). */
    void addEventListener(EventListener l);
    void removeEventListener(EventListener l);

    /**
     * Send {@code chat.send} request. The response arrives via streaming
     * {@code chat.delta} events and terminates with {@code chat.complete}.
     *
     * @param sessionKey target session
     * @param text user message
     * @param callback ack callback (request accepted by server)
     */
    void chatSend(String sessionKey, String text, AckCallback callback);

    /** Cancel the currently streaming chat on the given session. */
    void chatCancel(String sessionKey);

    /** Request the list of sessions. */
    void sessionsList(ResultCallback<String> callback);

    /** Create a new session. Returns session key. */
    void sessionsCreate(String label, ResultCallback<String> callback);

    /** Load history for a session. */
    void sessionsHistory(String sessionKey, ResultCallback<String> callback);

    // ---- Listener types ----

    interface ConnectionStateListener {
        void onConnectionState(ConnectionState state);
    }

    interface EventListener {
        void onEvent(GatewayEvent event);
    }

    interface AckCallback {
        void onAck(boolean ok, String errorOrNull);
    }

    interface ResultCallback<T> {
        void onResult(boolean ok, T payloadOrNull, String errorOrNull);
    }
}
