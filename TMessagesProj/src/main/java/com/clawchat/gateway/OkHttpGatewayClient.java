// DEPENDENCY: com.squareup.okhttp3:okhttp:4.12.0 — WebSocket transport for gateway client.
// (Verified 2026-04-06: no okhttp entry currently present in TMessagesProj/build.gradle.)
package com.clawchat.gateway;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.clawchat.gateway.model.ConnectionState;
import com.clawchat.gateway.model.GatewayEvent;
import com.clawchat.gateway.model.GatewayResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * OkHttp-based implementation of {@link GatewayClient}.
 *
 * <p>Performs the challenge-response handshake
 * ({@code connect.challenge} -> {@code connect} -> {@code hello-ok}) and provides
 * request/response correlation and event dispatch to registered listeners. Event
 * and connection-state listeners are always invoked on the Android main thread.
 *
 * <p>Ported from {@code OkHttpGatewayClient.kt}.
 */
public class OkHttpGatewayClient implements GatewayClient {

    private static final String TAG = "GatewayClient";
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000L;
    private static final long REQUEST_TIMEOUT_MS = 30_000L;

    private final Context appContext;
    private final GatewayAuth auth;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ConcurrentHashMap<String, PendingRequest> pending = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ConnectionStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<EventListener> eventListeners = new CopyOnWriteArrayList<>();

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile WebSocket webSocket;
    private volatile OkHttpClient httpClient;
    private volatile String currentUrl;
    private volatile boolean handshakeComplete = false;
    private volatile long tickIntervalMs = 15_000L;

    /** Optional callback used by {@link ConnectionManager} to observe disconnects. */
    private volatile DisconnectListener disconnectListener;

    public interface DisconnectListener {
        void onDisconnect(int code, String reason);
    }

    public OkHttpGatewayClient(Context context, GatewayAuth auth) {
        this.appContext = context.getApplicationContext();
        this.auth = auth;
    }

    public void setDisconnectListener(DisconnectListener l) {
        this.disconnectListener = l;
    }

    public long getTickIntervalMs() {
        return tickIntervalMs;
    }

    // ---- GatewayClient API ----

    @Override
    public void connect() {
        connectTo(com.clawchat.ClawChatConfig.get(appContext).getGatewayUrlPrimary());
    }

    /** Package-private entry used by {@link ConnectionManager} to target a specific URL. */
    void connectTo(String url) {
        if (state == ConnectionState.CONNECTING
                || state == ConnectionState.HANDSHAKING
                || state == ConnectionState.CONNECTED) {
            return;
        }
        setState(ConnectionState.CONNECTING);
        currentUrl = url;
        handshakeComplete = false;

        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .build();
        }

        Request req = new Request.Builder().url(url).build();
        webSocket = httpClient.newWebSocket(req, new Listener());

        // Handshake timeout watchdog.
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!handshakeComplete && state != ConnectionState.CONNECTED
                        && state != ConnectionState.DISCONNECTED) {
                    Log.w(TAG, "Handshake timed out");
                    forceClose(1002, "handshake timeout");
                }
            }
        }, HANDSHAKE_TIMEOUT_MS);
    }

    @Override
    public void disconnect() {
        WebSocket ws = webSocket;
        if (ws != null) {
            try {
                ws.close(1000, "client disconnect");
            } catch (Exception ignored) {
            }
        }
        webSocket = null;
        setState(ConnectionState.DISCONNECTED);
        cancelAllPending("disconnected");
    }

    @Override
    public ConnectionState getConnectionState() {
        return state;
    }

    @Override
    public void addConnectionStateListener(ConnectionStateListener l) {
        if (l != null) stateListeners.add(l);
    }

    @Override
    public void removeConnectionStateListener(ConnectionStateListener l) {
        stateListeners.remove(l);
    }

    @Override
    public void addEventListener(EventListener l) {
        if (l != null) eventListeners.add(l);
    }

    @Override
    public void removeEventListener(EventListener l) {
        eventListeners.remove(l);
    }

    @Override
    public void chatSend(String sessionKey, String text, final AckCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put("sessionKey", sessionKey);
            params.put("text", text);
        } catch (JSONException ignored) {
        }
        sendRequest("chat.send", params, new ResultCallback<String>() {
            @Override
            public void onResult(boolean ok, String payloadOrNull, String errorOrNull) {
                if (callback != null) callback.onAck(ok, errorOrNull);
            }
        });
    }

    @Override
    public void chatCancel(String sessionKey) {
        JSONObject params = new JSONObject();
        try {
            params.put("sessionKey", sessionKey);
        } catch (JSONException ignored) {
        }
        sendRequest("chat.cancel", params, null);
    }

    @Override
    public void sessionsList(ResultCallback<String> callback) {
        sendRequest("sessions.list", new JSONObject(), callback);
    }

    @Override
    public void sessionsCreate(String label, ResultCallback<String> callback) {
        JSONObject params = new JSONObject();
        try {
            if (label != null) params.put("label", label);
        } catch (JSONException ignored) {
        }
        sendRequest("sessions.create", params, callback);
    }

    @Override
    public void sessionsHistory(String sessionKey, ResultCallback<String> callback) {
        JSONObject params = new JSONObject();
        try {
            params.put("sessionKey", sessionKey);
        } catch (JSONException ignored) {
        }
        sendRequest("sessions.history", params, callback);
    }

    // ---- Request plumbing ----

    private void sendRequest(String method, JSONObject params, final ResultCallback<String> callback) {
        final String id = UUID.randomUUID().toString();
        JSONObject frame = new JSONObject();
        try {
            frame.put("type", "req");
            frame.put("id", id);
            frame.put("method", method);
            frame.put("params", params != null ? params : new JSONObject());
        } catch (JSONException e) {
            if (callback != null) postCallbackFail(callback, "frame encode failed");
            return;
        }

        WebSocket ws = webSocket;
        if (ws == null || state != ConnectionState.CONNECTED) {
            if (callback != null) postCallbackFail(callback, "not connected");
            return;
        }

        if (callback != null) {
            final PendingRequest pr = new PendingRequest(callback);
            pending.put(id, pr);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    PendingRequest removed = pending.remove(id);
                    if (removed != null && !removed.completed) {
                        removed.completed = true;
                        removed.callback.onResult(false, null, "timeout");
                    }
                }
            }, REQUEST_TIMEOUT_MS);
        }

        boolean sent = ws.send(frame.toString());
        if (!sent) {
            pending.remove(id);
            if (callback != null) postCallbackFail(callback, "send failed");
        }
    }

    @SuppressWarnings("unchecked")
    private void postCallbackFail(final ResultCallback<?> callback, final String err) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                ((ResultCallback<Object>) callback).onResult(false, null, err);
            }
        });
    }

    private void cancelAllPending(String reason) {
        List<PendingRequest> snapshot = new ArrayList<>(pending.values());
        pending.clear();
        for (final PendingRequest pr : snapshot) {
            if (pr.completed) continue;
            pr.completed = true;
            final String r = reason;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    pr.callback.onResult(false, null, r);
                }
            });
        }
    }

    // ---- State + dispatch ----

    private void setState(final ConnectionState s) {
        if (state == s) return;
        state = s;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (ConnectionStateListener l : stateListeners) {
                    try {
                        l.onConnectionState(s);
                    } catch (Exception e) {
                        Log.e(TAG, "state listener threw", e);
                    }
                }
            }
        });
    }

    private void dispatchEvent(final GatewayEvent ev) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (EventListener l : eventListeners) {
                    try {
                        l.onEvent(ev);
                    } catch (Exception e) {
                        Log.e(TAG, "event listener threw", e);
                    }
                }
            }
        });
    }

    private void forceClose(int code, String reason) {
        WebSocket ws = webSocket;
        if (ws != null) {
            try {
                ws.close(code, reason);
            } catch (Exception ignored) {
            }
        }
        handleClose(code, reason);
    }

    private void handleClose(int code, String reason) {
        webSocket = null;
        handshakeComplete = false;
        setState(ConnectionState.DISCONNECTED);
        cancelAllPending("connection closed: " + code + " " + reason);
        DisconnectListener dl = disconnectListener;
        if (dl != null) {
            try {
                dl.onDisconnect(code, reason);
            } catch (Exception e) {
                Log.e(TAG, "disconnect listener threw", e);
            }
        }
    }

    // ---- Frame handling ----

    private void handleFrame(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String type = json.optString("type", "");
            if ("event".equals(type)) {
                handleEventFrame(json);
            } else if ("res".equals(type)) {
                handleResponseFrame(json);
            } else {
                Log.w(TAG, "Unknown frame type: " + type);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Malformed frame: " + text, e);
        }
    }

    private void handleEventFrame(JSONObject json) throws JSONException {
        String eventName = json.getString("event");
        JSONObject payload = json.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();

        if ("connect.challenge".equals(eventName)) {
            // Handshake step 1: send connect req with bearer + device id + nonce.
            setState(ConnectionState.HANDSHAKING);
            String nonce = payload.optString("nonce", null);
            JSONObject params = auth.buildConnectParams(nonce);
            JSONObject frame = new JSONObject();
            frame.put("type", "req");
            frame.put("id", UUID.randomUUID().toString());
            frame.put("method", "connect");
            frame.put("params", params);
            WebSocket ws = webSocket;
            if (ws != null) ws.send(frame.toString());
            return;
        }

        Long seq = json.has("seq") ? json.getLong("seq") : null;
        Long stateVersion = json.has("stateVersion") ? json.getLong("stateVersion") : null;
        dispatchEvent(new GatewayEvent(eventName, payload, seq, stateVersion));
    }

    private void handleResponseFrame(JSONObject json) throws JSONException {
        String id = json.getString("id");
        boolean ok = json.optBoolean("ok", false);
        JSONObject payload = json.optJSONObject("payload");
        String error = json.isNull("error") ? null : json.optString("error", null);

        // hello-ok detection: server's initial policy response completes the handshake.
        if (!handshakeComplete && ok && payload != null && payload.has("policy")) {
            JSONObject policy = payload.optJSONObject("policy");
            if (policy != null) {
                tickIntervalMs = policy.optLong("tickIntervalMs", 15_000L);
            }
            handshakeComplete = true;
            setState(ConnectionState.CONNECTED);
        }

        final PendingRequest pr = pending.remove(id);
        if (pr == null || pr.completed) return;
        pr.completed = true;

        final boolean fOk = ok;
        final String fErr = error;
        final String fPayload = payload != null ? payload.toString() : null;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                pr.callback.onResult(fOk, fPayload, fErr);
            }
        });
    }

    @SuppressWarnings("unused")
    private GatewayResponse buildResponseUnused(String id, boolean ok, JSONObject payload, String error) {
        // Retained for parity with shared model; current callback type is String.
        return new GatewayResponse(id, ok, payload, error);
    }

    // ---- Inner types ----

    private static final class PendingRequest {
        final ResultCallback<String> callback;
        volatile boolean completed = false;

        PendingRequest(ResultCallback<String> callback) {
            this.callback = callback;
        }
    }

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            Log.d(TAG, "WebSocket opened: " + currentUrl);
            setState(ConnectionState.HANDSHAKING);
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            handleFrame(text);
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            try {
                ws.close(code, reason);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + code + " " + reason);
            handleClose(code, reason);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure", t);
            handleClose(1006, t.getMessage() != null ? t.getMessage() : "unknown error");
        }
    }
}
