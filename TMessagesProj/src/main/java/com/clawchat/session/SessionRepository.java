package com.clawchat.session;

import android.os.Handler;
import android.os.Looper;

import com.clawchat.gateway.GatewayClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Repository that wraps {@link GatewayClient} session RPCs and marshals JSON
 * payloads into typed POJOs ({@link SessionInfo}, {@link HistoryMessage}).
 *
 * <p>All callbacks are posted to the Android main thread for safe UI binding.
 *
 * <p>Owned by Agent B (feat/conversation-list).
 */
public final class SessionRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final GatewayClient gateway;
    private final Handler main = new Handler(Looper.getMainLooper());

    public SessionRepository(GatewayClient gateway) {
        this.gateway = gateway;
    }

    // -------- Public API --------

    public void listSessions(final Callback<List<SessionInfo>> cb) {
        gateway.sessionsList(new GatewayClient.ResultCallback<String>() {
            @Override
            public void onResult(boolean ok, String payloadOrNull, String errorOrNull) {
                if (!ok || payloadOrNull == null) {
                    postError(cb, errorOrNull != null ? errorOrNull : "sessions.list failed");
                    return;
                }
                try {
                    final List<SessionInfo> out = parseSessions(payloadOrNull);
                    Collections.sort(out, new Comparator<SessionInfo>() {
                        @Override public int compare(SessionInfo a, SessionInfo b) {
                            return Long.compare(b.lastActivityMs, a.lastActivityMs);
                        }
                    });
                    postSuccess(cb, out);
                } catch (Exception e) {
                    postError(cb, "parse error: " + e.getMessage());
                }
            }
        });
    }

    public void createSession(final String label, final Callback<SessionInfo> cb) {
        gateway.sessionsCreate(label, new GatewayClient.ResultCallback<String>() {
            @Override
            public void onResult(boolean ok, String payloadOrNull, String errorOrNull) {
                if (!ok || payloadOrNull == null) {
                    postError(cb, errorOrNull != null ? errorOrNull : "sessions.create failed");
                    return;
                }
                try {
                    postSuccess(cb, parseCreatedSession(payloadOrNull, label));
                } catch (Exception e) {
                    postError(cb, "parse error: " + e.getMessage());
                }
            }
        });
    }

    public void loadHistory(final String sessionKey, final Callback<List<HistoryMessage>> cb) {
        gateway.sessionsHistory(sessionKey, new GatewayClient.ResultCallback<String>() {
            @Override
            public void onResult(boolean ok, String payloadOrNull, String errorOrNull) {
                if (!ok || payloadOrNull == null) {
                    postError(cb, errorOrNull != null ? errorOrNull : "sessions.history failed");
                    return;
                }
                try {
                    postSuccess(cb, parseHistory(payloadOrNull));
                } catch (Exception e) {
                    postError(cb, "parse error: " + e.getMessage());
                }
            }
        });
    }

    // -------- JSON parsing --------

    /**
     * Expected format:
     * <pre>{"sessions":[{"key":"...","label":"...","lastActivityMs":...,"lastMessage":"...","agentId":"..."}]}</pre>
     * Also tolerates a bare top-level JSON array.
     */
    static List<SessionInfo> parseSessions(String json) throws Exception {
        List<SessionInfo> out = new ArrayList<>();
        JSONArray arr;
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            arr = new JSONArray(trimmed);
        } else {
            JSONObject root = new JSONObject(trimmed);
            arr = root.optJSONArray("sessions");
            if (arr == null) return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String sk = o.optString("key", "");
            if (sk.isEmpty()) sk = o.optString("sessionKey", "");
            out.add(new SessionInfo(
                    sk,
                    o.optString("label", ""),
                    o.optLong("lastActivityMs", 0L),
                    o.optString("lastMessage", ""),
                    o.optString("agentId", "")
            ));
        }
        return out;
    }

    /**
     * Expected: either a bare session object, {@code {"session":{...}}}, or just
     * {@code {"key":"..."}} with only the id — in which case we synthesize with
     * the caller-supplied label and current timestamp.
     */
    static SessionInfo parseCreatedSession(String json, String fallbackLabel) throws Exception {
        JSONObject root = new JSONObject(json.trim());
        JSONObject o = root.optJSONObject("session");
        if (o == null) o = root;
        String key = o.optString("key", "");
        if (key.isEmpty()) key = o.optString("sessionKey", "");
        if (key.isEmpty()) throw new IllegalStateException("missing session key");
        return new SessionInfo(
                key,
                o.optString("label", fallbackLabel != null ? fallbackLabel : key),
                o.optLong("lastActivityMs", System.currentTimeMillis()),
                o.optString("lastMessage", ""),
                o.optString("agentId", "")
        );
    }

    /**
     * Expected format:
     * <pre>{"messages":[{"id":"...","role":"user","content":"...","timestampMs":...}]}</pre>
     */
    public static List<HistoryMessage> parseHistory(String json) throws Exception {
        List<HistoryMessage> out = new ArrayList<>();
        JSONObject root = new JSONObject(json.trim());
        JSONArray arr = root.optJSONArray("messages");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            out.add(new HistoryMessage(
                    o.optString("id", ""),
                    o.optString("role", "assistant"),
                    o.optString("content", ""),
                    o.optLong("timestampMs", 0L)
            ));
        }
        return out;
    }

    // -------- Main-thread dispatch helpers --------

    private <T> void postSuccess(final Callback<T> cb, final T v) {
        if (cb == null) return;
        main.post(new Runnable() { @Override public void run() { cb.onSuccess(v); } });
    }

    private <T> void postError(final Callback<T> cb, final String msg) {
        if (cb == null) return;
        main.post(new Runnable() { @Override public void run() { cb.onError(msg); } });
    }
}
