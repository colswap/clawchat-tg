package com.clawchat.gateway.model;

import org.json.JSONObject;

/**
 * Incoming event frame from the gateway.
 *
 * <p>Maps to protocol frame: {@code {type:"event", event, payload, seq?, stateVersion?}}
 */
public final class GatewayEvent {
    public final String event;
    public final JSONObject payload;
    public final Long seq;            // nullable
    public final Long stateVersion;   // nullable

    public GatewayEvent(String event, JSONObject payload, Long seq, Long stateVersion) {
        this.event = event;
        this.payload = payload;
        this.seq = seq;
        this.stateVersion = stateVersion;
    }
}
