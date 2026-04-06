package com.clawchat.session;

/**
 * POJO for a single historical message in a session, as returned by
 * {@code sessions.history}.
 *
 * <p>Owned by Agent B (feat/conversation-list).
 */
public final class HistoryMessage {

    public final String id;
    /** "user" | "assistant" | "system" | "tool" (free-form; callers should be lenient). */
    public final String role;
    public final String content;
    public final long timestampMs;

    public HistoryMessage(String id, String role, String content, long timestampMs) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestampMs = timestampMs;
    }
}
