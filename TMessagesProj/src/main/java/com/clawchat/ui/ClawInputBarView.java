package com.clawchat.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Minimal chat input bar for ClawChat. Not an attempt to recreate
 * {@code ChatActivityEnterView} — we just need a multi-line text field and
 * a send button that flips to a stop button during streaming.
 *
 * <p>Send/Stop icon swap is delegated to {@link StopButtonController}.
 */
public class ClawInputBarView extends FrameLayout {

    public interface OnSendListener {
        void onSend(String text);
    }

    public interface OnStopListener {
        void onStop();
    }

    private final EditText editText;
    private final ImageView actionButton;
    private final StopButtonController stopController;

    public ClawInputBarView(Context context) {
        super(context);
        setBackgroundColor(Color.parseColor("#FAFAFA"));
        int padPx = dp(8);
        setPadding(padPx, padPx, padPx, padPx);

        editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setHint("Message ClawChat");
        editText.setBackground(null);
        editText.setMaxLines(6);
        editText.setTextSize(16);
        FrameLayout.LayoutParams etLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        etLp.rightMargin = dp(48);
        etLp.gravity = Gravity.CENTER_VERTICAL;
        addView(editText, etLp);

        actionButton = new ImageView(context);
        actionButton.setImageResource(android.R.drawable.ic_menu_send);
        actionButton.setContentDescription("Send");
        FrameLayout.LayoutParams abLp = new FrameLayout.LayoutParams(
                dp(40), dp(40));
        abLp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        addView(actionButton, abLp);

        stopController = new StopButtonController(
                actionButton, editText,
                new StopButtonController.OnSendRequested() {
                    @Override public void onSend(String text) {
                        if (sendListener != null && text != null && !text.isEmpty()) {
                            sendListener.onSend(text);
                            editText.setText("");
                        }
                    }
                },
                new StopButtonController.OnStopRequested() {
                    @Override public void onStop() {
                        if (stopListener != null) stopListener.onStop();
                    }
                });
    }

    private OnSendListener sendListener;
    private OnStopListener stopListener;

    public void setOnSendListener(OnSendListener l) { this.sendListener = l; }
    public void setOnStopListener(OnStopListener l) { this.stopListener = l; }

    public void setStreaming(boolean streaming) {
        if (streaming) stopController.onStreamingStarted();
        else stopController.onStreamingEnded();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
