package com.clawchat.dialog;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Converts {@link ClawMessageStore.Entry} instances into the Telegram
 * {@link MessageObject} wrapper that {@code ChatMessageCell} expects.
 *
 * <p>ClawChat messages never touch the network — these TLRPC objects exist
 * purely in memory to drive Telegram's bubble renderer. The peer ids are
 * synthetic (derived from {@code dialogId}) and the {@code from_id} is a
 * dummy user id that is never looked up against Telegram servers.
 */
public final class ClawMessageObjectFactory {
    private ClawMessageObjectFactory() {}

    /** Dummy "user" id for ClawChat bot (assistant) messages. */
    public static final long BOT_USER_ID = 0x1L << 40;
    /** Dummy "user" id for the local ClawChat user (self). */
    public static final long SELF_USER_ID = (0x1L << 40) + 1L;

    private static final AtomicInteger ID_SEQ = new AtomicInteger(1);

    /** Build a MessageObject suitable for rendering via ChatMessageCell. */
    public static MessageObject fromEntry(ClawMessageStore.Entry entry, long dialogId) {
        int currentAccount = UserConfig.selectedAccount;

        TLRPC.Message msg = new TLRPC.TL_message();
        msg.id = ID_SEQ.getAndIncrement();
        msg.message = entry.content == null ? "" : entry.content;
        msg.date = (int) (entry.timestampMs / 1000L);
        msg.dialog_id = dialogId;
        msg.out = entry.role == ClawMessageStore.Role.USER;
        msg.unread = false;
        msg.media_unread = false;

        TLRPC.TL_peerUser fromPeer = new TLRPC.TL_peerUser();
        fromPeer.user_id = msg.out ? SELF_USER_ID : BOT_USER_ID;
        msg.from_id = fromPeer;

        // peer_id is set to a synthetic "user" peer for this dialog so the
        // cell does not try to look up chat/channel metadata.
        TLRPC.TL_peerUser toPeer = new TLRPC.TL_peerUser();
        toPeer.user_id = BOT_USER_ID; // dialog "with" the bot
        msg.peer_id = toPeer;

        // Minimal required field for TL_message — an empty action slot would
        // mark it as a service message; we want a plain text message.
        msg.entities = new java.util.ArrayList<>();
        msg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

        return new MessageObject(currentAccount, msg, /*generateLayout=*/ true, /*checkMediaExists=*/ false);
    }
}
