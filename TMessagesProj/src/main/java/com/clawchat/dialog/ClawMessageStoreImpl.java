package com.clawchat.dialog;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link ClawMessageStore}.
 *
 * <p>Holds messages per virtual dialog id. Thread-safe via a
 * {@link ConcurrentHashMap} keyed on dialogId, with a lock on each per-dialog
 * list for mutation. Listener dispatch is marshalled to the main thread so UI
 * code can invalidate views safely.
 *
 * <p>No Android UI dependencies — only {@link Handler} / {@link Looper} for
 * main-thread dispatch.
 */
public final class ClawMessageStoreImpl implements ClawMessageStore {

    private final ConcurrentHashMap<Long, List<Entry>> byDialog = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<Entry> bucket(long dialogId) {
        List<Entry> list = byDialog.get(dialogId);
        if (list == null) {
            list = new ArrayList<>();
            List<Entry> prev = byDialog.putIfAbsent(dialogId, list);
            if (prev != null) list = prev;
        }
        return list;
    }

    @Override
    public List<Entry> messagesFor(long dialogId) {
        List<Entry> list = byDialog.get(dialogId);
        if (list == null) return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    @Override
    public void addListener(Listener l) {
        if (l != null) listeners.addIfAbsent(l);
    }

    @Override
    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged(final long dialogId) {
        if (listeners.isEmpty()) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) {
                    try {
                        l.onMessagesChanged(dialogId);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    /** Look up the sessionKey of any message already held for this dialog. */
    private String sessionKeyFor(long dialogId) {
        List<Entry> list = byDialog.get(dialogId);
        if (list == null) return null;
        synchronized (list) {
            if (list.isEmpty()) return null;
            return list.get(0).sessionKey;
        }
    }

    /** Append a user message with status COMPLETE. */
    public void addUserMessage(long dialogId, String text) {
        List<Entry> list = bucket(dialogId);
        synchronized (list) {
            String sk = list.isEmpty() ? null : list.get(0).sessionKey;
            Entry e = new Entry(
                    "u-" + System.nanoTime(),
                    sk,
                    Role.USER,
                    text == null ? "" : text,
                    System.currentTimeMillis(),
                    Status.COMPLETE);
            list.add(e);
        }
        notifyChanged(dialogId);
    }

    /**
     * Variant that carries the sessionKey explicitly — used by the router so
     * newly-created dialogs get a sessionKey on their very first user message.
     */
    public void addUserMessage(long dialogId, String sessionKey, String text) {
        List<Entry> list = bucket(dialogId);
        synchronized (list) {
            Entry e = new Entry(
                    "u-" + System.nanoTime(),
                    sessionKey,
                    Role.USER,
                    text == null ? "" : text,
                    System.currentTimeMillis(),
                    Status.COMPLETE);
            list.add(e);
        }
        notifyChanged(dialogId);
    }

    /** Append an empty assistant message in STREAMING state. */
    public void beginAssistant(long dialogId, String messageId) {
        beginAssistant(dialogId, null, messageId);
    }

    public void beginAssistant(long dialogId, String sessionKey, String messageId) {
        List<Entry> list = bucket(dialogId);
        synchronized (list) {
            String sk = sessionKey;
            if (sk == null && !list.isEmpty()) sk = list.get(0).sessionKey;
            Entry e = new Entry(
                    messageId,
                    sk,
                    Role.ASSISTANT,
                    "",
                    System.currentTimeMillis(),
                    Status.STREAMING);
            list.add(e);
        }
        notifyChanged(dialogId);
    }

    /** Append delta chunk to the assistant entry with the given messageId. */
    public void appendDelta(long dialogId, String messageId, String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        List<Entry> list = bucket(dialogId);
        boolean mutated = false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                if (e.role == Role.ASSISTANT && messageId != null && messageId.equals(e.id)) {
                    Entry updated = new Entry(
                            e.id, e.sessionKey, e.role, e.content + chunk, e.timestampMs, Status.STREAMING);
                    list.set(i, updated);
                    mutated = true;
                    break;
                }
            }
        }
        if (mutated) notifyChanged(dialogId);
    }

    /** Flip the assistant entry to COMPLETE. */
    public void completeAssistant(long dialogId, String messageId) {
        List<Entry> list = bucket(dialogId);
        boolean mutated = false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                if (e.role == Role.ASSISTANT && messageId != null && messageId.equals(e.id)) {
                    Entry updated = new Entry(
                            e.id, e.sessionKey, e.role, e.content, e.timestampMs, Status.COMPLETE);
                    list.set(i, updated);
                    mutated = true;
                    break;
                }
            }
        }
        if (mutated) notifyChanged(dialogId);
    }

    /** Mark the assistant entry with status ERROR. Used by the router on ack failure. */
    /**
     * Insert a historical message that is already complete (e.g. loaded from
     * {@code sessions.history}). Unlike {@link #beginAssistant} / {@link #appendDelta},
     * this is a single-shot insert with terminal status.
     */
    public void addCompletedMessage(long dialogId, String sessionKey, Role role,
                                    String id, String content, long timestampMs) {
        List<Entry> list = bucket(dialogId);
        synchronized (list) {
            Entry e = new Entry(
                    id != null ? id : java.util.UUID.randomUUID().toString(),
                    sessionKey, role, content == null ? "" : content,
                    timestampMs > 0 ? timestampMs : System.currentTimeMillis(),
                    Status.COMPLETE);
            list.add(e);
        }
        notifyChanged(dialogId);
    }

    /** Wipe all entries for a dialog. Used when reloading history. */
    public void clearDialog(long dialogId) {
        List<Entry> list = bucket(dialogId);
        synchronized (list) {
            list.clear();
        }
        notifyChanged(dialogId);
    }

    public void errorAssistant(long dialogId, String messageId) {
        List<Entry> list = bucket(dialogId);
        boolean mutated = false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                if (e.role == Role.ASSISTANT && messageId != null && messageId.equals(e.id)) {
                    Entry updated = new Entry(
                            e.id, e.sessionKey, e.role, e.content, e.timestampMs, Status.ERROR);
                    list.set(i, updated);
                    mutated = true;
                    break;
                }
            }
        }
        if (mutated) notifyChanged(dialogId);
    }

    /** Find the first dialog id whose messages carry the given sessionKey. */
    public Long dialogIdForSessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        for (Long dialogId : byDialog.keySet()) {
            if (sessionKey.equals(sessionKeyFor(dialogId))) return dialogId;
        }
        return null;
    }
}
