// DEPENDENCY: com.squareup.okhttp3:okhttp:4.12.0 — transitive via OkHttpGatewayClient.
package com.clawchat.gateway;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.clawchat.ClawChatConfig;
import com.clawchat.gateway.model.ConnectionState;

import java.util.Random;

/**
 * Reconnection + dual-host failover driver for {@link OkHttpGatewayClient}.
 *
 * <p>Owned by Agent A (feat/gateway). Implements exponential backoff
 * (initial 1s, cap 30s, jitter) and fails over to the fallback host after
 * {@link #FAILOVER_AFTER_ATTEMPTS} consecutive failures on the primary URL.
 *
 * <p>Ported from {@code ConnectionManager.kt}. Android {@code ConnectivityManager}
 * network-callback integration is intentionally omitted in this port; the state
 * listener path is sufficient to drive reconnection and the manager exposes
 * {@link #forceReconnect()} for external network-change triggers.
 */
public final class ConnectionManager implements GatewayClient.ConnectionStateListener,
        OkHttpGatewayClient.DisconnectListener {

    private static final String TAG = "ConnectionManager";
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 30_000L;
    private static final int FAILOVER_AFTER_ATTEMPTS = 3;

    private final Context appContext;
    private final OkHttpGatewayClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private volatile boolean enabled = false;
    private volatile boolean usingFallback = false;
    private int primaryFailures = 0;
    private long backoffMs = INITIAL_BACKOFF_MS;
    private Runnable pendingReconnect;

    public ConnectionManager(Context context, OkHttpGatewayClient client) {
        this.appContext = context.getApplicationContext();
        this.client = client;
    }

    /** Start managing the client: register listeners and trigger an initial connect. */
    public void start() {
        if (enabled) return;
        enabled = true;
        client.addConnectionStateListener(this);
        client.setDisconnectListener(this);
        scheduleReconnect(true);
    }

    /** Stop managing: unregister listeners, cancel pending, and disconnect. */
    public void stop() {
        enabled = false;
        cancelPending();
        client.removeConnectionStateListener(this);
        client.setDisconnectListener(null);
        client.disconnect();
    }

    /** Force an immediate reconnection attempt (e.g. on network-available). */
    public void forceReconnect() {
        if (!enabled) return;
        backoffMs = INITIAL_BACKOFF_MS;
        scheduleReconnect(true);
    }

    // ---- ConnectionStateListener ----

    @Override
    public void onConnectionState(ConnectionState state) {
        if (state == ConnectionState.CONNECTED) {
            Log.d(TAG, "Connected; resetting backoff");
            backoffMs = INITIAL_BACKOFF_MS;
            primaryFailures = 0;
        }
    }

    // ---- DisconnectListener ----

    @Override
    public void onDisconnect(int code, String reason) {
        if (!enabled) return;
        Log.d(TAG, "Disconnected (" + code + " " + reason + "); scheduling reconnect");
        if (!usingFallback) {
            primaryFailures++;
            if (primaryFailures >= FAILOVER_AFTER_ATTEMPTS) {
                Log.w(TAG, "Primary failed " + primaryFailures + " times; switching to fallback");
                usingFallback = true;
            }
        }
        scheduleReconnect(false);
    }

    // ---- Internals ----

    private void cancelPending() {
        if (pendingReconnect != null) {
            handler.removeCallbacks(pendingReconnect);
            pendingReconnect = null;
        }
    }

    private void scheduleReconnect(boolean immediate) {
        cancelPending();
        if (!enabled) return;

        long delay;
        if (immediate) {
            delay = 0L;
        } else {
            // Jitter: +/- 20% of current backoff.
            long jitter = (long) (backoffMs * 0.2 * (random.nextDouble() * 2.0 - 1.0));
            delay = Math.max(0L, backoffMs + jitter);
            backoffMs = Math.min(backoffMs * 2L, MAX_BACKOFF_MS);
        }

        pendingReconnect = new Runnable() {
            @Override
            public void run() {
                pendingReconnect = null;
                if (!enabled) return;
                String url = resolveUrl();
                Log.d(TAG, "Connecting to " + url + " (fallback=" + usingFallback + ")");
                try {
                    client.connectTo(url);
                } catch (Exception e) {
                    Log.e(TAG, "connectTo threw", e);
                    scheduleReconnect(false);
                }
            }
        };
        handler.postDelayed(pendingReconnect, delay);
    }

    private String resolveUrl() {
        ClawChatConfig cfg = ClawChatConfig.get(appContext);
        if (usingFallback) {
            String fb = cfg.getGatewayUrlFallback();
            if (fb != null && fb.length() > 0) return fb;
            return cfg.getGatewayUrlPrimary();
        }
        return cfg.getGatewayUrlPrimary();
    }
}
