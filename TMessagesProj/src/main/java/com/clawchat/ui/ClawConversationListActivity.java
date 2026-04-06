package com.clawchat.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clawchat.dialog.ClawDialogBridge;
import com.clawchat.session.SessionInfo;
import com.clawchat.session.SessionRepository;

import java.util.ArrayList;
import java.util.List;

// DEPENDENCY: androidx.appcompat:appcompat
// DEPENDENCY: androidx.recyclerview:recyclerview

/**
 * Claude.ai-style conversation list / session sidebar for ClawChat.
 *
 * <p>Shows a RecyclerView of {@link SessionInfo} with loading / empty / error
 * states and a floating "+ New conversation" button. On row click allocates a
 * ClawChat virtual dialog id via {@link ClawDialogBridge} and launches
 * {@code org.telegram.ui.LaunchActivity} with the {@code CLAW_OPEN_DIALOG_ID}
 * extra — the LaunchActivity hook (owned by the coordinator) routes this into
 * the ClawChat message screen.
 *
 * <p>Owned by Agent B (feat/conversation-list).
 */
public class ClawConversationListActivity extends AppCompatActivity {

    public static final String EXTRA_CLAW_OPEN_DIALOG_ID = "CLAW_OPEN_DIALOG_ID";

    private static final String LAUNCH_ACTIVITY_CLASS = "org.telegram.ui.LaunchActivity";

    private SessionRepository repository;

    // View states
    private FrameLayout root;
    private RecyclerView recyclerView;
    private ProgressBar loadingView;
    private TextView emptyView;
    private LinearLayout errorView;
    private TextView errorText;

    private SessionAdapter adapter;
    private final List<SessionInfo> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Provided by Agent F's ClawBootstrap
        repository = new SessionRepository(com.clawchat.ClawBootstrap.gatewayClient());

        setContentView(buildUi());
        loadSessions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when returning from a chat screen so the preview/timestamp updates.
        if (adapter != null) loadSessions();
    }

    // -------- UI construction --------

    private View buildUi() {
        root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFFF7F7F8);

        // Header
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView header = new TextView(this);
        header.setText("Conversations");
        header.setTextSize(20f);
        header.setTextColor(0xFF111111);
        int pad = dp(16);
        header.setPadding(pad, dp(20), pad, dp(12));
        header.setBackgroundColor(Color.WHITE);
        container.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Content area that swaps between recycler/empty/loading/error
        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        container.addView(content, contentLp);

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SessionAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setBackgroundColor(Color.WHITE);
        content.addView(recyclerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        loadingView = new ProgressBar(this);
        FrameLayout.LayoutParams lvLp = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.CENTER);
        content.addView(loadingView, lvLp);

        emptyView = new TextView(this);
        emptyView.setText("No conversations yet.\nTap + to start a new one.");
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(0xFF888888);
        emptyView.setTextSize(15f);
        content.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorText = new TextView(this);
        errorText.setTextColor(0xFFCC3333);
        errorText.setTextSize(14f);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(pad, pad, pad, dp(8));
        errorView.addView(errorText);
        TextView retry = new TextView(this);
        retry.setText("Tap to retry");
        retry.setTextColor(0xFF2266CC);
        retry.setTextSize(14f);
        retry.setPadding(pad, dp(4), pad, pad);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadSessions(); }
        });
        errorView.addView(retry);
        content.addView(errorView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        root.addView(container);

        // Floating "+ New conversation"
        TextView fab = new TextView(this);
        fab.setText("+  New conversation");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(14f);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(dp(20), dp(14), dp(20), dp(14));
        fab.setBackgroundColor(0xFF2266CC);
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        fabLp.rightMargin = dp(20);
        fabLp.bottomMargin = dp(24);
        fab.setLayoutParams(fabLp);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showNewConversationDialog(); }
        });
        root.addView(fab);

        return root;
    }

    // -------- State machine --------

    private enum State { LOADING, LIST, EMPTY, ERROR }

    private void setState(State s, String errMsg) {
        recyclerView.setVisibility(s == State.LIST ? View.VISIBLE : View.GONE);
        loadingView.setVisibility(s == State.LOADING ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(s == State.EMPTY ? View.VISIBLE : View.GONE);
        errorView.setVisibility(s == State.ERROR ? View.VISIBLE : View.GONE);
        if (s == State.ERROR) {
            errorText.setText(errMsg != null ? errMsg : "Failed to load conversations.");
        }
    }

    // -------- Data flow --------

    private void loadSessions() {
        setState(State.LOADING, null);
        repository.listSessions(new SessionRepository.Callback<List<SessionInfo>>() {
            @Override public void onSuccess(List<SessionInfo> result) {
                items.clear();
                if (result != null) items.addAll(result);
                adapter.notifyDataSetChanged();
                setState(items.isEmpty() ? State.EMPTY : State.LIST, null);
            }
            @Override public void onError(String message) {
                setState(State.ERROR, message);
            }
        });
    }

    private void showNewConversationDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Conversation name");
        int pad = dp(20);
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, dp(8), pad, 0);
        wrap.addView(input, lp);

        new AlertDialog.Builder(this)
                .setTitle("New conversation")
                .setView(wrap)
                .setPositiveButton("Create", (d, w) -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) label = "New conversation";
                    createAndOpen(label);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createAndOpen(String label) {
        setState(State.LOADING, null);
        repository.createSession(label, new SessionRepository.Callback<SessionInfo>() {
            @Override public void onSuccess(SessionInfo result) {
                openSession(result);
            }
            @Override public void onError(String message) {
                setState(items.isEmpty() ? State.EMPTY : State.LIST, null);
                Toast.makeText(ClawConversationListActivity.this,
                        "Create failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openSession(SessionInfo s) {
        if (s == null || s.key == null || s.key.isEmpty()) return;
        try {
            ClawDialogBridge bridge = com.clawchat.dialog.ClawSingletons.bridge();
            long dialogId = bridge.allocateDialogId(s.key);

            Intent intent = new Intent();
            intent.setClassName(getPackageName(), LAUNCH_ACTIVITY_CLASS);
            intent.putExtra(EXTRA_CLAW_OPEN_DIALOG_ID, dialogId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to open conversation: " + t.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // -------- Adapter --------

    private final class SessionAdapter extends RecyclerView.Adapter<SessionVH> {
        @NonNull @Override
        public SessionVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            SessionRowView row = new SessionRowView(parent.getContext());
            return new SessionVH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull SessionVH holder, int position) {
            final SessionInfo s = items.get(position);
            holder.row.bind(s);
            holder.row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openSession(s); }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static final class SessionVH extends RecyclerView.ViewHolder {
        final SessionRowView row;
        SessionVH(SessionRowView row) {
            super(row);
            this.row = row;
        }
    }

    /** Convenience entry point for launching this activity. */
    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, ClawConversationListActivity.class);
    }
}
