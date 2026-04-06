package com.clawchat.gateway.model;

import org.json.JSONObject;

/**
 * Response frame from the gateway matched by request id.
 * Maps to protocol frame: {@code {type:"res", id, ok, payload?, error?}}
 */
public final class GatewayResponse {
    public final String id;
    public final boolean ok;
    public final JSONObject payload; // nullable
    public final String error;       // nullable

    public GatewayResponse(String id, boolean ok, JSONObject payload, String error) {
        this.id = id;
        this.ok = ok;
        this.payload = payload;
        this.error = error;
    }
}
