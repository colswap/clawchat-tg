package com.clawchat.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.clawchat.session.SessionInfo;

/**
 * Programmatic row view for a single {@link SessionInfo} in the ClawChat
 * conversation list. No XML; keeps the Agent B drop-in fully self-contained.
 *
 * <p>Owned by Agent B (feat/conversation-list).
 */
final class SessionRowView extends LinearLayout {

    private final TextView labelView;
    private final TextView timeView;
    private final TextView previewView;

    SessionRowView(Context ctx) {
        super(ctx);
        setOrientation(VERTICAL);
        int pad = dp(16);
        setPadding(pad, dp(12), pad, dp(12));
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setClickable(true);
        setFocusable(true);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(HORIZONTAL);
        top.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        labelView = new TextView(ctx);
        labelView.setTextSize(16f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setTextColor(0xFF111111);
        labelView.setSingleLine(true);
        LayoutParams lLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        top.addView(labelView, lLp);

        timeView = new TextView(ctx);
        timeView.setTextSize(12f);
        timeView.setTextColor(0xFF888888);
        timeView.setGravity(Gravity.END);
        top.addView(timeView, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        addView(top);

        previewView = new TextView(ctx);
        previewView.setTextSize(13f);
        previewView.setTextColor(0xFF666666);
        previewView.setMaxLines(2);
        previewView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams pLp = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        pLp.topMargin = dp(4);
        addView(previewView, pLp);

        // Bottom divider (cheap)
        setBackgroundColor(Color.WHITE);
    }

    void bind(SessionInfo s) {
        labelView.setText(s.label == null || s.label.isEmpty() ? "(untitled)" : s.label);
        previewView.setText(s.lastMessage == null ? "" : s.lastMessage);
        if (s.lastActivityMs > 0) {
            CharSequence rel = DateUtils.getRelativeTimeSpanString(
                    s.lastActivityMs, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            timeView.setText(rel);
        } else {
            timeView.setText("");
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
