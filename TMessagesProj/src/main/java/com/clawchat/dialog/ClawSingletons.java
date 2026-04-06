package com.clawchat.dialog;

import android.content.Context;

import com.clawchat.gateway.GatewayClient;

/**
 * Lazy singleton holder for the dialog-bridge subsystem.
 *
 * <p>{@link #initialize(Context, GatewayClient)} must be called exactly once
 * by the coordinator (from {@code ClawBootstrap} — Agent F) before any
 * accessor is used. Each accessor throws {@link IllegalStateException} if
 * called before initialization.
 *
 * <p>DEPENDENCY: requires a constructed {@link GatewayClient} (Agent A) and an
 * application {@link Context}.
 */
public final class ClawSingletons {

    private static volatile ClawDialogBridgeImpl bridge;
    private static volatile ClawMessageStoreImpl store;
    private static volatile ClawMessageRouter router;

    private ClawSingletons() {}

    /** Idempotent: subsequent calls are no-ops. */
    public static synchronized void initialize(Context context, GatewayClient gatewayClient) {
        if (bridge != null) return;
        if (context == null) throw new IllegalArgumentException("context == null");
        if (gatewayClient == null) throw new IllegalArgumentException("gatewayClient == null");
        ClawDialogBridgeImpl b = new ClawDialogBridgeImpl(context);
        ClawMessageStoreImpl s = new ClawMessageStoreImpl();
        ClawMessageRouter r = new ClawMessageRouter(gatewayClient, b, s);
        bridge = b;
        store = s;
        router = r;
    }

    public static ClawDialogBridge bridge() {
        ClawDialogBridgeImpl b = bridge;
        if (b == null) throw new IllegalStateException("ClawSingletons not initialized");
        return b;
    }

    public static ClawMessageStore store() {
        ClawMessageStoreImpl s = store;
        if (s == null) throw new IllegalStateException("ClawSingletons not initialized");
        return s;
    }

    public static ClawMessageRouter router() {
        ClawMessageRouter r = router;
        if (r == null) throw new IllegalStateException("ClawSingletons not initialized");
        return r;
    }
}
