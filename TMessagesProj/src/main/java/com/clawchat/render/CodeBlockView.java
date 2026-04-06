// DEPENDENCY: io.noties.markwon:core:4.6.2
// DEPENDENCY: io.noties.markwon:ext-tables:4.6.2
// DEPENDENCY: io.noties.markwon:ext-strikethrough:4.6.2
// DEPENDENCY: io.noties.prism4j:prism4j:2.0.0
// DEPENDENCY: io.noties.prism4j:prism4j-bundler:2.0.0
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

import io.noties.prism4j.Prism4j;

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
 * Highlighting: uses Prism4j + a tiny inline theme. If the requested language is
 * not available in the bundled Prism4j grammars, falls back to plain text.
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
     * Attempts to highlight via Prism4j. Returns null on any failure so the caller
     * can fall back to plain text. Uses reflection-free API but wrapped in try/catch
     * because grammar availability depends on which prism4j-bundler langs are bundled.
     */
    private CharSequence tryPrismHighlight(String lang, String src) {
        try {
            // Prism4j requires a GrammarLocator, typically generated by prism4j-bundler.
            // We rely on the generated "io.noties.prism4j.GrammarLocatorDef" if present.
            Class<?> locatorCls = Class.forName("io.noties.prism4j.GrammarLocatorDef");
            Object locator = locatorCls.getDeclaredConstructor().newInstance();
            Prism4j prism4j = new Prism4j((Prism4j.GrammarLocator) locator);
            Prism4j.Grammar grammar = prism4j.grammar(normalizeLang(lang));
            if (grammar == null) return null;
            // Tokenize and convert to a simple Spanned with foreground colors per token type.
            java.util.List<? extends Prism4j.Node> nodes = prism4j.tokenize(src, grammar);
            return PrismSpanner.render(nodes);
        } catch (Throwable t) {
            return null;
        }
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

    /**
     * Minimal Prism4j node → Spanned renderer with a tiny built-in palette.
     * Kept private/static to avoid extra files.
     */
    private static final class PrismSpanner {
        static Spanned render(java.util.List<? extends Prism4j.Node> nodes) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            visit(nodes, sb);
            return sb;
        }

        private static void visit(java.util.List<? extends Prism4j.Node> nodes,
                                  android.text.SpannableStringBuilder sb) {
            for (Prism4j.Node n : nodes) {
                if (n.isSyntax()) {
                    Prism4j.Syntax s = (Prism4j.Syntax) n;
                    int start = sb.length();
                    visit(s.children(), sb);
                    int end = sb.length();
                    int color = colorFor(s.type());
                    sb.setSpan(new android.text.style.ForegroundColorSpan(color),
                            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    sb.append(((Prism4j.Text) n).literal());
                }
            }
        }

        private static int colorFor(String type) {
            if (type == null) return Color.parseColor("#E0E0E0");
            switch (type) {
                case "comment": case "prolog": case "doctype": case "cdata":
                    return Color.parseColor("#6C7086");
                case "keyword": case "selector": case "tag":
                    return Color.parseColor("#CBA6F7");
                case "string": case "char": case "attr-value":
                    return Color.parseColor("#A6E3A1");
                case "number": case "boolean": case "constant":
                    return Color.parseColor("#FAB387");
                case "function": case "class-name":
                    return Color.parseColor("#89B4FA");
                case "operator": case "punctuation":
                    return Color.parseColor("#94E2D5");
                case "variable": case "property":
                    return Color.parseColor("#F38BA8");
                default:
                    return Color.parseColor("#E0E0E0");
            }
        }
    }
}
