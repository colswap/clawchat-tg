// DEPENDENCY: io.noties.markwon:core:4.6.2
// DEPENDENCY: io.noties.markwon:ext-tables:4.6.2
// DEPENDENCY: io.noties.markwon:ext-strikethrough:4.6.2
package com.clawchat.render;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Displays a single syntax-highlighted code block.
 *
 * Layout:
 *   [ lang chip ]                [ copy button ]
 *   ┌────────────────────────────────────────────┐
 *   │ horizontally scrollable highlighted code   │
 *   └────────────────────────────────────────────┘
 *
 * Dark background (#1e1e2e), monospace typeface.
 *
        // removed: * Highlighting: uses Prism4j + a tiny inline theme. If the requested language is
        // removed: * not available in the bundled Prism4j grammars, falls back to plain text.
 */
public class CodeBlockView extends LinearLayout {

    private static final int BG_COLOR = 0xFF1E1E2E;
    private static final int CHIP_COLOR = 0xFF313244;
    private static final int FG_COLOR = 0xFFE0E0E0;

    private TextView langChip;
    private Button copyButton;
    private TextView codeView;

    private String language = "";
    private String code = "";

    public CodeBlockView(Context context) {
        super(context);
        init();
    }

    @SuppressLint("SetTextI18n")
    private void init() {
        setOrientation(VERTICAL);
        setBackgroundColor(BG_COLOR);
        int pad = dp(8);
        setPadding(pad, pad, pad, pad);

        // Header row
        FrameLayout header = new FrameLayout(getContext());
        LayoutParams headerLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        header.setLayoutParams(headerLp);

        langChip = new TextView(getContext());
        langChip.setText("text");
        langChip.setTextColor(FG_COLOR);
        langChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        langChip.setBackgroundColor(CHIP_COLOR);
        langChip.setPadding(dp(8), dp(2), dp(8), dp(2));
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        chipLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        header.addView(langChip, chipLp);

        copyButton = new Button(getContext());
        copyButton.setText("Copy");
        copyButton.setAllCaps(false);
        copyButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        copyButton.setTextColor(FG_COLOR);
        copyButton.setBackgroundColor(CHIP_COLOR);
        copyButton.setPadding(dp(10), dp(2), dp(10), dp(2));
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        copyLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        header.addView(copyButton, copyLp);

        copyButton.setOnClickListener(v -> copyToClipboard());

        addView(header);

        // Code (horizontally scrollable)
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        LayoutParams hsLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hsLp.topMargin = dp(6);
        hs.setLayoutParams(hsLp);
        hs.setHorizontalScrollBarEnabled(true);

        codeView = new TextView(getContext());
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        codeView.setTextColor(FG_COLOR);
        codeView.setHorizontallyScrolling(true);
        codeView.setPadding(dp(4), dp(4), dp(4), dp(4));
        hs.addView(codeView);

        addView(hs);
    }

    public void setCode(String language, String code) {
        this.language = language == null ? "" : language;
        this.code = code == null ? "" : code;
        langChip.setText(this.language.isEmpty() ? "text" : this.language);
        applyHighlighting();
    }

    private void applyHighlighting() {
        CharSequence styled = tryPrismHighlight(language, code);
        if (styled == null) {
            codeView.setText(code);
        } else {
            codeView.setText(styled);
        }
    }

    /**
        // removed: * Attempts to highlight via Prism4j. Returns null on any failure so the caller
     * can fall back to plain text. Uses reflection-free API but wrapped in try/catch
     * because grammar availability depends on which prism4j-bundler langs are bundled.
     */
    private CharSequence tryPrismHighlight(String lang, String src) {
        return null; // syntax highlighting disabled; add prism4j via jitpack to re-enable
    }

    private static String normalizeLang(String lang) {
        if (lang == null) return "";
        String l = lang.toLowerCase();
        switch (l) {
            case "js": return "javascript";
            case "ts": return "typescript";
            case "sh": case "shell": return "bash";
            case "py": return "python";
            case "kt": return "kotlin";
            default: return l;
        }
    }

    private void copyToClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("code", code));
                Toast.makeText(getContext(), "Copied", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {}
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
