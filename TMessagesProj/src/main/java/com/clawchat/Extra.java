package com.clawchat;

/**
 * Build/version constants for the ClawChat fork.
 *
 * <p>Owned by Agent F. Bump {@link #CLAW_VERSION} on each ClawChat release.
 * {@link #CLAW_BUILD_FLAVOR} should track the gradle flavor
 * ({@code dev}, {@code beta}, {@code release}).
 */
public final class Extra {

    /** Semver for the ClawChat layer (independent of upstream Telegram version). */
    public static final String CLAW_VERSION = "0.1.0";

    /** Build flavor: {@code dev}, {@code beta}, or {@code release}. */
    public static final String CLAW_BUILD_FLAVOR = "dev";

    /** User-Agent suffix appended to gateway WS handshake. */
    public static final String CLAW_USER_AGENT = "ClawChat-TG/" + CLAW_VERSION + " (" + CLAW_BUILD_FLAVOR + ")";

    /** Protocol version negotiated with the OpenClaw gateway. */
    public static final String CLAW_GATEWAY_PROTOCOL = "openclaw-ws-v3";

    /** Logcat tag prefix used throughout the ClawChat layer. */
    public static final String LOG_TAG = "ClawChat";

    /** Timeout (ms) for the settings "Test Connection" button. */
    public static final long TEST_CONNECTION_TIMEOUT_MS = 5000L;

    private Extra() {}
}
