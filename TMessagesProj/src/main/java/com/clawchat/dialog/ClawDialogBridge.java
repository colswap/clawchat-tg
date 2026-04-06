package com.clawchat.dialog;

/**
 * Bridge between ClawChat sessions and Telegram virtual dialog ids.
 *
 * <p>Owned by Agent C (feat/dialog-bridge). READ-ONLY for all other agents.
 *
 * <p>Virtual dialog ids are allocated in a range that cannot collide with any
 * real Telegram dialog id. They exist only in-process and never get sent to
 * Telegram servers.
 */
public interface ClawDialogBridge {

    /** True iff the given dialog id is a ClawChat virtual dialog. */
    boolean isClawDialog(long dialogId);

    /**
     * Return the OpenClaw session key for a ClawChat virtual dialog.
     * Returns null if the id is not a ClawChat dialog.
     */
    String sessionKeyFor(long dialogId);

    /**
     * Allocate (or retrieve existing) virtual dialog id for a session key.
     * Stable across app restarts within the same session set.
     */
    long allocateDialogId(String sessionKey);
}
