package com.clawchat.dialog;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Default implementation of {@link ClawDialogBridge}.
 *
 * <p>Allocates virtual dialog ids in the range
 * {@code [CLAW_DIALOG_BASE, CLAW_DIALOG_BASE + 1_000_000)}. The base is chosen
 * near {@link Long#MIN_VALUE} so it cannot collide with any real Telegram
 * dialog id.
 *
 * <p>The sessionKey &harr; dialogId bimap is persisted to
 * {@code SharedPreferences("clawchat_dialog_map")} as a JSON blob under the
 * key {@code "map"} on every allocation, so the mapping survives process
 * restart.
 */
public final class ClawDialogBridgeImpl implements ClawDialogBridge {

    public static final long CLAW_DIALOG_BASE = Long.MIN_VALUE + 1_000_000_000L;
    public static final long CLAW_DIALOG_MAX_EXCLUSIVE = CLAW_DIALOG_BASE + 1_000_000L;

    private static final String PREFS = "clawchat_dialog_map";
    private static final String KEY_MAP = "map";

    private final SharedPreferences prefs;
    private final Object lock = new Object();

    // sessionKey -> dialogId
    private final Map<String, Long> sessionToDialog = new HashMap<>();
    // dialogId -> sessionKey
    private final Map<Long, String> dialogToSession = new HashMap<>();

    private long nextId;

    public ClawDialogBridgeImpl(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        String json = prefs.getString(KEY_MAP, null);
        long maxId = CLAW_DIALOG_BASE - 1;
        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String sessionKey = keys.next();
                    long dialogId = obj.getLong(sessionKey);
                    sessionToDialog.put(sessionKey, dialogId);
                    dialogToSession.put(dialogId, sessionKey);
                    if (dialogId > maxId) maxId = dialogId;
                }
            } catch (JSONException ignored) {
                // Corrupt persisted map — start fresh.
            }
        }
        nextId = Math.max(CLAW_DIALOG_BASE, maxId + 1);
    }

    private void persistLocked() {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Long> e : sessionToDialog.entrySet()) {
                obj.put(e.getKey(), (long) e.getValue());
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(KEY_MAP, obj.toString()).apply();
    }

    @Override
    public boolean isClawDialog(long dialogId) {
        return dialogId >= CLAW_DIALOG_BASE && dialogId < CLAW_DIALOG_MAX_EXCLUSIVE;
    }

    @Override
    public String sessionKeyFor(long dialogId) {
        if (!isClawDialog(dialogId)) return null;
        synchronized (lock) {
            return dialogToSession.get(dialogId);
        }
    }

    @Override
    public long allocateDialogId(String sessionKey) {
        if (sessionKey == null) {
            throw new IllegalArgumentException("sessionKey == null");
        }
        synchronized (lock) {
            Long existing = sessionToDialog.get(sessionKey);
            if (existing != null) return existing;
            if (nextId >= CLAW_DIALOG_MAX_EXCLUSIVE) {
                throw new IllegalStateException("ClawChat virtual dialog id range exhausted");
            }
            long id = nextId++;
            sessionToDialog.put(sessionKey, id);
            dialogToSession.put(id, sessionKey);
            persistLocked();
            return id;
        }
    }
}
