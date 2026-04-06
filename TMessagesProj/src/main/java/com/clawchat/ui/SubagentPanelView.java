package com.clawchat.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.clawchat.ClawChatConfig;
import com.clawchat.gateway.SubagentManager;
import com.clawchat.gateway.SubagentState;

import java.util.List;

/**
 * Floating badge ("🤖 N agents running") that pulses while subagents are active,
 * and opens a {@link com.google.android.material.bottomsheet.BottomSheetDialog}
 * listing current subagent states when tapped.
 *
 * <p>DEPENDENCY: {@code com.google.android.material:material:1.12.0} (BottomSheetDialog)
 *
 * <p>Owned by Agent E (feat/subagent-panel).
 */
public class SubagentPanelView extends FrameLayout implements SubagentManager.Listener {

    private TextView badge;
    private ObjectAnimator pulseAnimator;
    @Nullable private SubagentManager manager;

    public SubagentPanelView(Context context) { super(context); init(); }
    public SubagentPanelView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public SubagentPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        badge = new TextView(getContext());
        int padH = dp(14), padV = dp(8);
        badge.setPadding(padH, padV, padH, padV);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(13f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xFF4A6FE3);
        bg.setCornerRadius(dp(20));
        badge.setBackground(bg);
        badge.setText("\uD83E\uDD16 0 agents running");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(8);
        addView(badge, lp);

        badge.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { showBottomSheet(); }
        });

        setVisibility(GONE);
    }

    /** Attach to a manager; auto-subscribes and updates on main thread. */
    public void attach(SubagentManager manager) {
        if (this.manager == manager) return;
        detach();
        this.manager = manager;
        if (manager != null) {
            manager.addListener(this);
            refresh();
        }
    }

    public void detach() {
        if (manager != null) {
            manager.removeListener(this);
            manager = null;
        }
        stopPulse();
        setVisibility(GONE);
    }

    @Override
    public void onSubagentStatesChanged() {
        refresh();
    }

    private void refresh() {
        boolean enabled = ClawChatConfig.get(getContext()).isSubagentPanelEnabled();
        int running = manager != null ? manager.runningCount() : 0;
        if (!enabled || running <= 0) {
            stopPulse();
            setVisibility(GONE);
            return;
        }
        badge.setText("\uD83E\uDD16 " + running + (running == 1 ? " agent running" : " agents running"));
        setVisibility(VISIBLE);
        startPulse();
    }

    private void startPulse() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ObjectAnimator.ofFloat(badge, "alpha", 1.0f, 0.6f);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        badge.setAlpha(1f);
    }

    private void showBottomSheet() {
        if (manager == null) return;
        List<SubagentState> states = manager.getStates();

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(getContext());
        title.setText("Subagents");
        title.setTextSize(18f);
        title.setTextColor(0xFF111111);
        title.setPadding(0, 0, 0, dp(12));
        container.addView(title);

        if (states.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No subagents active.");
            empty.setTextColor(0xFF666666);
            container.addView(empty);
        } else {
            for (SubagentState s : states) {
                TextView row = new TextView(getContext());
                String statusStr = s.status != null ? s.status.name() : "?";
                String label = s.label != null ? s.label : s.agentId;
                String line = "• " + label + "  [" + statusStr + "]";
                if (s.message != null && s.message.length() > 0) {
                    line += "\n    " + s.message;
                }
                row.setText(line);
                row.setTextColor(0xFF222222);
                row.setPadding(0, dp(6), 0, dp(6));
                container.addView(row);
            }
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
        dialog.setContentView(container);
        dialog.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
