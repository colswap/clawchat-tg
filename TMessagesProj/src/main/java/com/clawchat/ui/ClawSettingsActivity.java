package com.clawchat.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.clawchat.ClawBootstrap;
import com.clawchat.ClawChatConfig;
import com.clawchat.Extra;
import com.clawchat.gateway.GatewayClient;
import com.clawchat.gateway.model.ConnectionState;

// DEPENDENCY: androidx.appcompat:appcompat (for AppCompatActivity)
// DEPENDENCY: androidx.annotation:annotation

/**
 * Settings screen for the ClawChat layer.
 *
 * <p>Built programmatically (no XML) so the fork keeps its resource surface small
 * and stays isolated from upstream Telegram resources. Owned by Agent F.
 */
public class ClawSettingsActivity extends AppCompatActivity {

    private EditText primaryUrlField;
    private EditText fallbackUrlField;
    private EditText bearerTokenField;
    private Switch subagentPanelSwitch;
    private Switch artifactsSwitch;
    private Switch markdownSwitch;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("ClawChat Settings");

        ClawChatConfig cfg = ClawChatConfig.get(this);

        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // --- Gateway ---
        root.addView(sectionHeader("Gateway"));
        primaryUrlField = new Row(this, "Primary URL", cfg.getGatewayUrlPrimary(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI).editText;
        root.addView((View) primaryUrlField.getParent());
        fallbackUrlField = new Row(this, "Fallback URL", cfg.getGatewayUrlFallback(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI).editText;
        root.addView((View) fallbackUrlField.getParent());
        bearerTokenField = new Row(this, "Bearer Token", cfg.getBearerToken(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD).editText;
        root.addView((View) bearerTokenField.getParent());

        // --- Features ---
        root.addView(sectionHeader("Features"));
        subagentPanelSwitch = makeSwitch("Subagent Panel", cfg.isSubagentPanelEnabled());
        root.addView(subagentPanelSwitch);
        artifactsSwitch = makeSwitch("Artifacts", cfg.isArtifactEnabled());
        root.addView(artifactsSwitch);
        markdownSwitch = makeSwitch("Markdown Rendering", cfg.isMarkdownEnabled());
        root.addView(markdownSwitch);

        // --- Actions ---
        root.addView(sectionHeader("Actions"));

        Button testBtn = new Button(this);
        testBtn.setText("Test Connection");
        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onTestConnection(); }
        });
        root.addView(testBtn);

        Button saveBtn = new Button(this);
        saveBtn.setText("Save");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSave(); }
        });
        root.addView(saveBtn);

        // --- About ---
        root.addView(sectionHeader("About"));
        TextView version = new TextView(this);
        version.setText("Version " + Extra.CLAW_VERSION + " (" + Extra.CLAW_BUILD_FLAVOR + ")");
        version.setPadding(0, dp(4), 0, dp(4));
        root.addView(version);

        setContentView(scroll);
    }

    private void onSave() {
        ClawChatConfig.get(this).edit()
                .putString(ClawChatConfig.KEY_GATEWAY_URL_PRIMARY, primaryUrlField.getText().toString().trim())
                .putString(ClawChatConfig.KEY_GATEWAY_URL_FALLBACK, fallbackUrlField.getText().toString().trim())
                .putString(ClawChatConfig.KEY_BEARER_TOKEN, bearerTokenField.getText().toString())
                .putBoolean(ClawChatConfig.KEY_SUBAGENT_PANEL_ENABLED, subagentPanelSwitch.isChecked())
                .putBoolean(ClawChatConfig.KEY_ARTIFACT_ENABLED, artifactsSwitch.isChecked())
                .putBoolean(ClawChatConfig.KEY_MARKDOWN_ENABLED, markdownSwitch.isChecked())
                .apply();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private void onTestConnection() {
        // Persist the current field values first so the gateway picks them up on connect().
        onSaveSilent();

        final GatewayClient client;
        try {
            client = ClawBootstrap.gatewayClient();
        } catch (IllegalStateException ex) {
            Toast.makeText(this, "Bootstrap not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show();

        final boolean[] done = { false };
        final GatewayClient.ConnectionStateListener[] listenerHolder = new GatewayClient.ConnectionStateListener[1];

        final Runnable timeout = new Runnable() {
            @Override public void run() {
                synchronized (done) {
                    if (done[0]) return;
                    done[0] = true;
                }
                if (listenerHolder[0] != null) {
                    client.removeConnectionStateListener(listenerHolder[0]);
                }
                Toast.makeText(ClawSettingsActivity.this, "Failed: timeout", Toast.LENGTH_LONG).show();
            }
        };

        listenerHolder[0] = new GatewayClient.ConnectionStateListener() {
            @Override public void onConnectionState(final ConnectionState state) {
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (state == ConnectionState.CONNECTED) {
                            synchronized (done) {
                                if (done[0]) return;
                                done[0] = true;
                            }
                            mainHandler.removeCallbacks(timeout);
                            client.removeConnectionStateListener(listenerHolder[0]);
                            Toast.makeText(ClawSettingsActivity.this, "Connected \u2713", Toast.LENGTH_LONG).show();
                        } else if (state == ConnectionState.ERROR) {
                            synchronized (done) {
                                if (done[0]) return;
                                done[0] = true;
                            }
                            mainHandler.removeCallbacks(timeout);
                            client.removeConnectionStateListener(listenerHolder[0]);
                            Toast.makeText(ClawSettingsActivity.this,
                                    "Failed: " + state, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        };

        client.addConnectionStateListener(listenerHolder[0]);
        mainHandler.postDelayed(timeout, Extra.TEST_CONNECTION_TIMEOUT_MS);
        client.connect();
    }

    private void onSaveSilent() {
        ClawChatConfig.get(this).edit()
                .putString(ClawChatConfig.KEY_GATEWAY_URL_PRIMARY, primaryUrlField.getText().toString().trim())
                .putString(ClawChatConfig.KEY_GATEWAY_URL_FALLBACK, fallbackUrlField.getText().toString().trim())
                .putString(ClawChatConfig.KEY_BEARER_TOKEN, bearerTokenField.getText().toString())
                .apply();
    }

    // ---- view helpers ----

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setPadding(0, dp(16), 0, dp(8));
        tv.setGravity(Gravity.START);
        return tv;
    }

    private Switch makeSwitch(String label, boolean checked) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setChecked(checked);
        s.setPadding(0, dp(8), 0, dp(8));
        return s;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** A labeled EditText row. Kept inline to avoid spreading the settings UI across files. */
    static final class Row extends LinearLayout {
        final EditText editText;

        Row(AppCompatActivity ctx, String label, String initial, int inputType) {
            super(ctx);
            setOrientation(VERTICAL);
            int pad = (int) (4 * ctx.getResources().getDisplayMetrics().density + 0.5f);
            setPadding(0, pad, 0, pad);

            TextView tv = new TextView(ctx);
            tv.setText(label);
            addView(tv);

            editText = new EditText(ctx);
            editText.setInputType(inputType);
            editText.setText(initial == null ? "" : initial);
            editText.setSingleLine(true);
            addView(editText);
        }
    }
}
