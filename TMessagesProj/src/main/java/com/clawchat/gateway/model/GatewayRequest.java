package com.clawchat.gateway.model;

import org.json.JSONObject;

/**
 * Outgoing request frame to the gateway.
 * Maps to protocol frame: {@code {type:"req", id, method, params}}
 */
public final class GatewayRequest {
    public final String id;
    public final String method;
    public final JSONObject params;

    public GatewayRequest(String id, String method, JSONObject params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }
}
