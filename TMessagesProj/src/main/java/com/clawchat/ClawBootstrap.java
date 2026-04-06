package com.clawchat;

import android.app.Application;
import android.util.Log;

import com.clawchat.dialog.ClawDialogBridgeImpl;
import com.clawchat.dialog.ClawMessageRouter;
import com.clawchat.dialog.ClawMessageStoreImpl;
import com.clawchat.dialog.ClawSingletons;
import com.clawchat.gateway.GatewayClient;
import com.clawchat.gateway.SubagentManager;

/**
 * Process-wide singleton bootstrap for the ClawChat layer.
 *
 * <p>Called exactly once from {@code LaunchActivity} (wired in by the coordinator).
 * Creates the gateway client, subagent manager, dialog bridge, message store,
 * and message router, and installs them as singletons so the rest of the
 * Telegram codebase can reach them via static accessors.
 *
 * <p>Owned by Agent F.
 */
public final class ClawBootstrap {

    private static final String TAG = Extra.LOG_TAG + ".Bootstrap";

    private static volatile boolean initialized = false;
    private static GatewayClient gatewayClient;
    private static SubagentManager subagentManager;

    private ClawBootstrap() {}

    /**
     * Initialize all ClawChat singletons. Safe to call more than once — subsequent
     * invocations are no-ops.
     */
    public static synchronized void initialize(Application app) {
        if (initialized) {
            Log.d(TAG, "initialize() called again; ignoring");
            return;
        }
        if (app == null) {
            throw new IllegalArgumentException("Application must not be null");
        }

        Log.i(TAG, "ClawChat bootstrap starting (version=" + Extra.CLAW_VERSION
                + ", flavor=" + Extra.CLAW_BUILD_FLAVOR + ")");

        ClawChatConfig config = ClawChatConfig.get(app);

        // Gateway client — provided by Agent A.
        gatewayClient = new com.clawchat.gateway.OkHttpGatewayClient(app, config); // Provided by Agent A

        // Subagent manager — provided by Agent E. It subscribes to gateway events.
        subagentManager = new SubagentManager(gatewayClient);
        gatewayClient.addEventListener(subagentManager);

        // Dialog bridge / message store / router — provided by Agent C.
        ClawDialogBridgeImpl dialogBridge = new ClawDialogBridgeImpl(app);
        ClawMessageStoreImpl messageStore = new ClawMessageStoreImpl();
        ClawMessageRouter messageRouter = new ClawMessageRouter(gatewayClient, dialogBridge, messageStore);

        ClawSingletons.initialize(dialogBridge, messageStore, messageRouter);

        initialized = true;
        Log.i(TAG, "ClawChat bootstrap complete");
    }

    /** @return the process-wide {@link GatewayClient}. */
    public static GatewayClient gatewayClient() {
        if (!initialized || gatewayClient == null) {
            throw new IllegalStateException("ClawBootstrap.initialize() has not been called");
        }
        return gatewayClient;
    }

    /** @return the process-wide {@link SubagentManager}. */
    public static SubagentManager subagentManager() {
        if (!initialized || subagentManager == null) {
            throw new IllegalStateException("ClawBootstrap.initialize() has not been called");
        }
        return subagentManager;
    }

    /** Test hook. */
    public static boolean isInitialized() {
        return initialized;
    }
}
