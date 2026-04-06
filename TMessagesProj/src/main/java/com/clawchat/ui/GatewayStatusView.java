package com.clawchat.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.clawchat.gateway.GatewayClient;
import com.clawchat.gateway.model.ConnectionState;

/**
 * Small colored banner reflecting {@link ConnectionState}.
 *
 * <p>Auto-hides 2 seconds after reaching {@link ConnectionState#CONNECTED}.
 * Owned by Agent E (feat/subagent-panel).
 */
public class GatewayStatusView extends FrameLayout implements GatewayClient.ConnectionStateListener {

    private TextView text;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private GatewayClient client;

    private final Runnable hideRunnable = new Runnable() {
        @Override public void run() { setVisibility(GONE); }
    };

    public GatewayStatusView(Context context) { super(context); init(); }
    public GatewayStatusView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public GatewayStatusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        text = new TextView(getContext());
        text.setTextColor(Color.WHITE);
        text.setTextSize(12f);
        text.setGravity(Gravity.CENTER);
        int padV = dp(6), padH = dp(12);
        text.setPadding(padH, padV, padH, padV);
        addView(text, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setVisibility(GONE);
    }

    /** Attach to a gateway client and subscribe to connection state updates. */
    public void attach(GatewayClient c) {
        if (this.client == c) return;
        detach();
        this.client = c;
        if (c != null) {
            c.addConnectionStateListener(this);
            onConnectionState(c.getConnectionState());
        }
    }

    public void detach() {
        if (client != null) {
            client.removeConnectionStateListener(this);
            client = null;
        }
        handler.removeCallbacks(hideRunnable);
    }

    @Override
    public void onConnectionState(final ConnectionState state) {
        handler.post(new Runnable() {
            @Override public void run() { render(state); }
        });
    }

    private void render(ConnectionState state) {
        if (state == null) { setVisibility(GONE); return; }
        handler.removeCallbacks(hideRunnable);
        String label;
        int color;
        switch (state) {
            case CONNECTING:
            case HANDSHAKING:
                label = "Connecting\u2026"; color = 0xFFB07D00; break;
            case CONNECTED:
                label = "Connected"; color = 0xFF2E7D32; break;
            case RECONNECTING:
                label = "Reconnecting\u2026"; color = 0xFFB07D00; break;
            case ERROR:
                label = "Offline"; color = 0xFFB00020; break;
            case DISCONNECTED:
            default:
                label = "Offline"; color = 0xFF555555; break;
        }
        text.setText(label);
        setBackgroundColor(color);
        setVisibility(VISIBLE);
        if (state == ConnectionState.CONNECTED) {
            handler.postDelayed(hideRunnable, 2000L);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
