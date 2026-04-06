package com.clawchat.ui;

import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Controls the send/stop toggle on the composer button.
 *
 * <p>While a chat message is streaming, the button becomes a "stop" button whose
 * click cancels the stream. When idle, it acts as the normal send button.
 *
 * <p>Owned by Agent E (feat/subagent-panel).
 */
public final class StopButtonController {

    public interface OnSendRequested {
        void onSend(String text);
    }

    public interface OnStopRequested {
        void onStop();
    }

    private final ImageView button;
    private final EditText input;
    @Nullable private final OnSendRequested onSend;
    @Nullable private final OnStopRequested onStop;

    private int sendIconRes = android.R.drawable.ic_menu_send;
    private int stopIconRes = android.R.drawable.ic_media_pause;

    private boolean streaming;

    public StopButtonController(ImageView button, EditText input,
                                @Nullable OnSendRequested onSend,
                                @Nullable OnStopRequested onStop) {
        this.button = button;
        this.input = input;
        this.onSend = onSend;
        this.onStop = onStop;
        this.button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { handleClick(); }
        });
        render();
    }

    /** Override default icons (e.g. ic_send / ic_stop from the app's drawables). */
    public void setIcons(int sendIconRes, int stopIconRes) {
        this.sendIconRes = sendIconRes;
        this.stopIconRes = stopIconRes;
        render();
    }

    public void onStreamingStarted() {
        streaming = true;
        render();
    }

    public void onStreamingEnded() {
        streaming = false;
        render();
    }

    public boolean isStreaming() {
        return streaming;
    }

    private void handleClick() {
        if (streaming) {
            if (onStop != null) onStop.onStop();
        } else {
            CharSequence cs = input != null ? input.getText() : null;
            String text = cs != null ? cs.toString() : "";
            if (onSend != null) onSend.onSend(text);
        }
    }

    private void render() {
        button.setImageResource(streaming ? stopIconRes : sendIconRes);
        button.setContentDescription(streaming ? "Stop" : "Send");
    }
}
