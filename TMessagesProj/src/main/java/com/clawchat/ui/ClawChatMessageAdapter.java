package com.clawchat.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clawchat.dialog.ClawMessageObjectFactory;
import com.clawchat.dialog.ClawMessageStore;
import com.clawchat.render.ClawMessageOverlay;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter with two view types:
 * <ul>
 *   <li>USER (type 0) — rendered via Telegram's {@link ChatMessageCell} for
 *       pixel-identical outgoing bubble styling.</li>
 *   <li>ASSISTANT/SYSTEM (type 1) — rendered via our own
 *       {@link ClawMessageOverlay} so markdown / code blocks / artifacts show
 *       up properly, wrapped in a Telegram-like rounded bubble.</li>
 * </ul>
 *
 * <p>On every bind we construct a fresh {@link MessageObject} for user rows so
 * text-layout caches inside {@code ChatMessageCell} cannot stale during
 * streaming updates.
 */
public class ClawChatMessageAdapter extends RecyclerView.Adapter<ClawChatMessageAdapter.Holder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private final Context context;
    private final long dialogId;
    private final List<ClawMessageStore.Entry> entries = new ArrayList<>();

    public ClawChatMessageAdapter(Context context, long dialogId) {
        this.context = context;
        this.dialogId = dialogId;
        setHasStableIds(false);
    }

    public void setEntries(List<ClawMessageStore.Entry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    public int count() { return entries.size(); }

    @Override
    public int getItemViewType(int position) {
        ClawMessageStore.Entry e = entries.get(position);
        return e.role == ClawMessageStore.Role.USER ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            FrameLayout container = new FrameLayout(context);
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            ChatMessageCell cell = new ChatMessageCell(
                    context, org.telegram.messenger.UserConfig.selectedAccount);
            cell.setDelegate(null);
            container.addView(cell, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(container, cell, null);
        } else {
            // ASSISTANT / SYSTEM — custom bubble with ClawMessageOverlay inside.
            FrameLayout container = new FrameLayout(context);
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int vPad = dp(context, 4);
            int hPad = dp(context, 12);
            container.setPadding(hPad, vPad, hPad, vPad);

            FrameLayout bubble = new FrameLayout(context);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#FFFFFF"));
            bg.setCornerRadius(dp(context, 14));
            bg.setStroke(dp(context, 1), Color.parseColor("#E6E6E6"));
            bubble.setBackground(bg);
            int innerPad = dp(context, 10);
            bubble.setPadding(innerPad, innerPad, innerPad, innerPad);

            FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            bubbleLp.gravity = Gravity.START;
            bubbleLp.rightMargin = dp(context, 48); // leave room so bubble does not stretch full-width
            container.addView(bubble, bubbleLp);

            ClawMessageOverlay overlay = new ClawMessageOverlay(context);
            bubble.addView(overlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));

            return new Holder(container, null, overlay);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ClawMessageStore.Entry entry = entries.get(position);
        try {
            if (holder.cell != null) {
                boolean firstInChat = position == 0;
                boolean lastInChat = position == entries.size() - 1;
                MessageObject mo = ClawMessageObjectFactory.fromEntry(entry, dialogId);
                holder.cell.setMessageObject(mo, null, false, false, firstInChat, lastInChat);
            } else if (holder.overlay != null) {
                holder.overlay.setMessage(entry.content == null ? "" : entry.content);
            }
        } catch (Throwable t) {
            android.util.Log.e("ClawChat", "bind failed @" + position, t);
        }
    }

    @Override
    public int getItemCount() { return entries.size(); }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ChatMessageCell cell;        // nullable — only for USER rows
        final ClawMessageOverlay overlay;  // nullable — only for ASSISTANT rows
        Holder(View root, ChatMessageCell cell, ClawMessageOverlay overlay) {
            super(root);
            this.cell = cell;
            this.overlay = overlay;
        }
    }
}
