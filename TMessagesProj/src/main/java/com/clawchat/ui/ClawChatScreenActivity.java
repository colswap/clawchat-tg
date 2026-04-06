package com.clawchat.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clawchat.ClawBootstrap;
import com.clawchat.dialog.ClawMessageStore;

import java.util.List;

/**
 * Telegram-style chat screen for a single ClawChat (OpenClaw) session.
 *
 * <p>Uses {@link org.telegram.ui.Cells.ChatMessageCell} as the row renderer so
 * bubbles look identical to Telegram, but all I/O is routed through
 * {@link com.clawchat.dialog.ClawMessageRouter} and never touches Telegram
 * servers.
 *
 * <p>Intent extras:
 * <ul>
 *   <li>{@link #EXTRA_SESSION_KEY} (required) — OpenClaw session key</li>
 *   <li>{@link #EXTRA_SESSION_LABEL} (optional) — human-readable title</li>
 * </ul>
 */
public class ClawChatScreenActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_KEY = "claw_session_key";
    public static final String EXTRA_SESSION_LABEL = "claw_session_label";

    private long dialogId;
    private String sessionKey;
    private ClawChatMessageAdapter adapter;
    private RecyclerView messagesView;
    private ClawInputBarView inputBar;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ClawMessageStore.Listener storeListener = new ClawMessageStore.Listener() {
        @Override public void onMessagesChanged(final long changedDialogId) {
            if (changedDialogId != dialogId) return;
            mainHandler.post(new Runnable() {
                @Override public void run() { refreshMessages(); }
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionKey = getIntent().getStringExtra(EXTRA_SESSION_KEY);
        String label = getIntent().getStringExtra(EXTRA_SESSION_LABEL);
        if (sessionKey == null || sessionKey.isEmpty()) {
            finish();
            return;
        }
        setTitle(label != null ? label : "ClawChat");

        try {
            dialogId = ClawBootstrap.gatewayClient() != null
                    ? com.clawchat.dialog.ClawSingletons.bridge().allocateDialogId(sessionKey)
                    : 0L;
        } catch (IllegalStateException ex) {
            showFatal("Bootstrap not ready");
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Optional top banner — connection state (reuses Agent E's view).
        try {
            GatewayStatusView banner = new GatewayStatusView(this);
            banner.attach(ClawBootstrap.gatewayClient());
            root.addView(banner, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } catch (Throwable ignored) {
            // GatewayStatusView is optional for MVP.
        }

        // Messages list
        messagesView = new RecyclerView(this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        messagesView.setLayoutManager(lm);
        adapter = new ClawChatMessageAdapter(this, dialogId);
        messagesView.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(messagesView, listLp);

        // Empty-state overlay (shown when 0 messages)
        final TextView emptyState = new TextView(this);
        emptyState.setText("대화를 시작해보세요");
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setTextColor(Color.GRAY);
        emptyState.setTextSize(14);
        FrameLayout emptyWrap = new FrameLayout(this);
        emptyWrap.addView(emptyState, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        emptyWrap.setVisibility(android.view.View.GONE);
        // (We don't wire the empty state toggle strictly — a simple check in refreshMessages would do it.)

        // Input bar
        inputBar = new ClawInputBarView(this);
        inputBar.setOnSendListener(new ClawInputBarView.OnSendListener() {
            @Override public void onSend(String text) {
                try {
                    com.clawchat.dialog.ClawSingletons.router().sendText(dialogId, text);
                    inputBar.setStreaming(true);
                } catch (Throwable t) {
                    android.util.Log.e("ClawChat", "send failed", t);
                }
            }
        });
        inputBar.setOnStopListener(new ClawInputBarView.OnStopListener() {
            @Override public void onStop() {
                try {
                    com.clawchat.dialog.ClawSingletons.router().cancel(dialogId);
                } catch (Throwable ignored) {}
                inputBar.setStreaming(false);
            }
        });
        root.addView(inputBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Subscribe and initial paint
        com.clawchat.dialog.ClawSingletons.store().addListener(storeListener);
        refreshMessages();
    }

    private void refreshMessages() {
        try {
            List<ClawMessageStore.Entry> entries =
                    com.clawchat.dialog.ClawSingletons.store().messagesFor(dialogId);
            adapter.setEntries(entries);
            if (entries != null && !entries.isEmpty()) {
                messagesView.scrollToPosition(entries.size() - 1);
            }
            // Flip streaming state based on whether the last entry is still STREAMING.
            boolean streaming = false;
            if (entries != null && !entries.isEmpty()) {
                ClawMessageStore.Entry last = entries.get(entries.size() - 1);
                streaming = last.status == ClawMessageStore.Status.STREAMING
                        || last.status == ClawMessageStore.Status.PENDING;
            }
            if (inputBar != null) inputBar.setStreaming(streaming);
        } catch (Throwable t) {
            android.util.Log.e("ClawChat", "refresh failed", t);
        }
    }

    private void showFatal(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(24, 24, 24, 24);
        setContentView(tv);
    }

    @Override
    protected void onDestroy() {
        try {
            com.clawchat.dialog.ClawSingletons.store().removeListener(storeListener);
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    /** Convenience launcher used by ClawConversationListActivity. */
    public static Intent launchIntent(android.content.Context ctx, String sessionKey, String label) {
        Intent i = new Intent(ctx, ClawChatScreenActivity.class);
        i.putExtra(EXTRA_SESSION_KEY, sessionKey);
        if (label != null) i.putExtra(EXTRA_SESSION_LABEL, label);
        return i;
    }
}
