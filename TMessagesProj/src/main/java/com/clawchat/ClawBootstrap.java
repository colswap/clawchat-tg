package com.clawchat;

import android.app.Application;
import android.util.Log;

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

        // Gateway auth (Agent A) — wraps ClawChatConfig + device id.
        com.clawchat.gateway.GatewayAuth auth = new com.clawchat.gateway.GatewayAuth(app);

        // Gateway client — Agent A. Ctor is (Context, GatewayAuth).
        gatewayClient = new com.clawchat.gateway.OkHttpGatewayClient(app, auth);

        // Subagent manager (Agent E) subscribes itself in its ctor; do not add again.
        subagentManager = new SubagentManager(gatewayClient);

        // Dialog singletons (Agent C). initialize() builds bridge/store/router internally.
        ClawSingletons.initialize(app, gatewayClient);

        // Preseed synthetic TLRPC.User entries so Telegram's ChatMessageCell
        // can look up names/avatars instead of returning null (which would
        // otherwise cause subtle rendering issues or NPEs).
        try {
            int currentAccount = org.telegram.messenger.UserConfig.selectedAccount;
            org.telegram.messenger.MessagesController mc =
                    org.telegram.messenger.MessagesController.getInstance(currentAccount);

            org.telegram.tgnet.TLRPC.TL_user bot = new org.telegram.tgnet.TLRPC.TL_user();
            bot.id = com.clawchat.dialog.ClawMessageObjectFactory.BOT_USER_ID;
            bot.first_name = "ClawChat";
            bot.last_name = "";
            bot.bot = true;
            bot.access_hash = 0L;
            mc.putUser(bot, /*fromCache=*/ true);

            org.telegram.tgnet.TLRPC.TL_user self = new org.telegram.tgnet.TLRPC.TL_user();
            self.id = com.clawchat.dialog.ClawMessageObjectFactory.SELF_USER_ID;
            self.first_name = "Me";
            self.last_name = "";
            self.access_hash = 0L;
            mc.putUser(self, /*fromCache=*/ true);

            Log.d(TAG, "preseeded ClawChat synthetic users");
        } catch (Throwable t) {
            Log.w(TAG, "preseed failed (continuing without name cache)", t);
        }

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
