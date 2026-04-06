package com.clawchat;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences wrapper for ClawChat settings.
 *
 * <p>Owned by Agent F (feat/settings). All other agents must treat this as READ-ONLY
 * and access only via public getters. Add new settings via PR to main agent, not by
 * editing directly.
 */
public final class ClawChatConfig {
    private static final String PREFS_NAME = "clawchat_prefs";

    // Keys
    public static final String KEY_GATEWAY_URL_PRIMARY = "gw_url_primary";
    public static final String KEY_GATEWAY_URL_FALLBACK = "gw_url_fallback";
    public static final String KEY_BEARER_TOKEN = "bearer_token";
    public static final String KEY_SUBAGENT_PANEL_ENABLED = "feat_subagent_panel";
    public static final String KEY_ARTIFACT_ENABLED = "feat_artifact";
    public static final String KEY_MARKDOWN_ENABLED = "feat_markdown";

    // Defaults
    public static final String DEFAULT_GATEWAY_URL_PRIMARY = "ws://192.168.1.1:18789/ws";
    public static final String DEFAULT_GATEWAY_URL_FALLBACK = "ws://100.98.150.110:18789/ws";

    private static volatile ClawChatConfig instance;
    private final SharedPreferences prefs;

    private ClawChatConfig(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static ClawChatConfig get(Context ctx) {
        if (instance == null) {
            synchronized (ClawChatConfig.class) {
                if (instance == null) instance = new ClawChatConfig(ctx);
            }
        }
        return instance;
    }

    public String getGatewayUrlPrimary() {
        return prefs.getString(KEY_GATEWAY_URL_PRIMARY, DEFAULT_GATEWAY_URL_PRIMARY);
    }

    public String getGatewayUrlFallback() {
        return prefs.getString(KEY_GATEWAY_URL_FALLBACK, DEFAULT_GATEWAY_URL_FALLBACK);
    }

    public String getBearerToken() {
        return prefs.getString(KEY_BEARER_TOKEN, "");
    }

    public boolean isSubagentPanelEnabled() {
        return prefs.getBoolean(KEY_SUBAGENT_PANEL_ENABLED, true);
    }

    public boolean isArtifactEnabled() {
        return prefs.getBoolean(KEY_ARTIFACT_ENABLED, true);
    }

    public boolean isMarkdownEnabled() {
        return prefs.getBoolean(KEY_MARKDOWN_ENABLED, true);
    }

    public SharedPreferences.Editor edit() {
        return prefs.edit();
    }
}
