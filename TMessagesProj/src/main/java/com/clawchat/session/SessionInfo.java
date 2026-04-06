package com.clawchat.session;

/**
 * Lightweight POJO describing a single OpenClaw chat session as rendered in the
 * ClawChat conversation list (Claude.ai-style sidebar).
 *
 * <p>Owned by Agent B (feat/conversation-list).
 */
public final class SessionInfo {

    public final String key;
    public final String label;
    public final long lastActivityMs;
    public final String lastMessage;
    public final String agentId;

    public SessionInfo(String key, String label, long lastActivityMs,
                       String lastMessage, String agentId) {
        this.key = key;
        this.label = label;
        this.lastActivityMs = lastActivityMs;
        this.lastMessage = lastMessage;
        this.agentId = agentId;
    }

    @Override
    public String toString() {
        return "SessionInfo{" + key + ",label=" + label + "}";
    }
}
