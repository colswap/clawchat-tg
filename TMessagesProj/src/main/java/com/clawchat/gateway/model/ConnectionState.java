package com.clawchat.gateway.model;

/** Gateway WebSocket connection state. */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
