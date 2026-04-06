package com.clawchat.gateway;

/**
 * Immutable-ish snapshot of a single subagent's execution state.
 *
 * <p>Owned by Agent E (feat/subagent-panel).
 */
public final class SubagentState {

    public enum Status {
        RUNNING,
        COMPLETED,
        ERROR
    }

    public String agentId;
    public String label;
    public Status status;
    public long startedAtMs;
    public String message;

    public SubagentState() {
    }

    public SubagentState(String agentId, String label, Status status, long startedAtMs, String message) {
        this.agentId = agentId;
        this.label = label;
        this.status = status;
        this.startedAtMs = startedAtMs;
        this.message = message;
    }
}
