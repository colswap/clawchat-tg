package com.clawchat.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clawchat.dialog.ClawMessageObjectFactory;
import com.clawchat.dialog.ClawMessageStore;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter that renders {@link ClawMessageStore.Entry} items
 * using Telegram's {@link ChatMessageCell} for pixel-identical bubble styling.
 *
 * <p>Each delta update rebuilds the affected entry into a fresh
 * {@link MessageObject} because {@code ChatMessageCell} caches text layout
 * internally; re-binding a brand new MessageObject is safer than mutating
 * the existing one in place.
 */
public class ClawChatMessageAdapter extends RecyclerView.Adapter<ClawChatMessageAdapter.Holder> {

    private final Context context;
    private final long dialogId;
    private final List<ClawMessageStore.Entry> entries = new ArrayList<>();

    public ClawChatMessageAdapter(Context context, long dialogId) {
        this.context = context;
        this.dialogId = dialogId;
        setHasStableIds(false);
    }

    /** Replace the full entry list and refresh. */
    public void setEntries(List<ClawMessageStore.Entry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    public int count() {
        return entries.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        ChatMessageCell cell = new ChatMessageCell(context, org.telegram.messenger.UserConfig.selectedAccount);
        cell.setDelegate(null); // all delegate methods are default — null is fine for read-only rendering
        container.addView(cell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(container, cell);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ClawMessageStore.Entry entry = entries.get(position);
        boolean firstInChat = position == 0;
        boolean lastInChat = position == entries.size() - 1;
        try {
            MessageObject mo = ClawMessageObjectFactory.fromEntry(entry, dialogId);
            holder.cell.setMessageObject(mo, null, false, false, firstInChat, lastInChat);
        } catch (Throwable t) {
            android.util.Log.e("ClawChat", "bind failed @" + position, t);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ChatMessageCell cell;
        Holder(View root, ChatMessageCell cell) {
            super(root);
            this.cell = cell;
        }
    }
}
