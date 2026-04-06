package com.clawchat.gateway;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.clawchat.ClawChatConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Device authentication helper for the OpenClaw gateway.
 *
 * <p>Owned by Agent A (feat/gateway). Maintains a persistent device id stored in
 * its own SharedPreferences file and builds the connect-request params used during
 * the gateway handshake. The bearer token is read from {@link ClawChatConfig}.
 *
 * <p>Ported from {@code DeviceAuth.kt}.
 */
public final class GatewayAuth {

    private static final String PREFS_NAME = "clawchat_device";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String CLIENT_ID = "openclaw-android";

    private final Context appContext;
    private final SharedPreferences prefs;

    public GatewayAuth(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the stable device id, generating and persisting one on first call. */
    public String getDeviceId() {
        String existing = prefs.getString(KEY_DEVICE_ID, null);
        if (existing != null && existing.length() > 0) {
            return existing;
        }
        String id = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        return id;
    }

    /** Current bearer token (may be empty string). */
    public String getBearerToken() {
        return ClawChatConfig.get(appContext).getBearerToken();
    }

    /**
     * Builds the {@code params} payload for the {@code connect} request issued in
     * response to {@code connect.challenge}.
     *
     * @param nonce nonce received from the server challenge (may be null)
     * @return a {@link JSONObject} containing deviceId, token, clientId, device metadata
     *         and — if non-null — the challenge nonce.
     */
    public JSONObject buildConnectParams(String nonce) {
        JSONObject o = new JSONObject();
        try {
            o.put("deviceId", getDeviceId());
            o.put("token", getBearerToken());
            o.put("clientId", CLIENT_ID);
            o.put("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
            o.put("os", "android");
            o.put("osVersion", Build.VERSION.RELEASE);
            if (nonce != null && nonce.length() > 0) {
                o.put("nonce", nonce);
            }
        } catch (JSONException e) {
            // JSONObject.put throws only on NaN/Infinity — unreachable here.
        }
        return o;
    }
}
