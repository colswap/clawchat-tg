package com.clawchat.dialog;

import java.util.List;

/**
 * In-memory message store for ClawChat virtual dialogs. Owned by Agent C.
 *
 * <p>Holds streaming-in-progress messages and completed history snapshots.
 * UI observes via {@link Listener} for invalidate triggers.
 */
public interface ClawMessageStore {

    /** A single message held in the store. */
    final class Entry {
        public final String id;
        public final String sessionKey;
        public final Role role;
        public final String content;
        public final long timestampMs;
        public final Status status;

        public Entry(String id, String sessionKey, Role role, String content, long timestampMs, Status status) {
            this.id = id;
            this.sessionKey = sessionKey;
            this.role = role;
            this.content = content;
            this.timestampMs = timestampMs;
            this.status = status;
        }
    }

    enum Role { USER, ASSISTANT, SYSTEM }
    enum Status { PENDING, STREAMING, COMPLETE, ERROR }

    /** All messages for the given ClawChat dialog, ordered ascending by timestamp. */
    List<Entry> messagesFor(long dialogId);

    /** Observe changes. */
    void addListener(Listener l);
    void removeListener(Listener l);

    interface Listener {
        void onMessagesChanged(long dialogId);
    }
}
