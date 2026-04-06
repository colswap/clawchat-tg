package com.clawchat.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Renders a rich agent-AI artifact with tabs [Code | Preview] and an expand button.
 *
 * Security (non-negotiable):
 *   - JavaScript enabled (required for Mermaid/React)
 *   - File access disabled
 *   - Content access disabled
 *   - File-URL cross-origin access disabled
 *   - All navigation intercepted and blocked (shouldOverrideUrlLoading returns true)
 *   - HTML loaded via loadDataWithBaseURL("about:blank", ...)
 *
 * NOTE: Mermaid and React templates currently pull JS from public CDNs.
 *       TODO: inline mermaid.min.js, react.production.min.js, react-dom.production.min.js,
 *             and babel.min.js into assets/ and load via file:///android_asset/.
 */
public class ArtifactView extends FrameLayout {

    private static final String MERMAID_CDN =
            "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js";
    // TODO: inline mermaid.min.js (above) — currently online dependency.

    private static final String REACT_CDN =
            "https://unpkg.com/react@18/umd/react.production.min.js";
    private static final String REACTDOM_CDN =
            "https://unpkg.com/react-dom@18/umd/react-dom.production.min.js";
    private static final String BABEL_CDN =
            "https://unpkg.com/@babel/standalone/babel.min.js";
    // TODO: inline react/react-dom/babel (above) — currently online dependencies.

    private Button tabCode;
    private Button tabPreview;
    private Button expandButton;
    private CodeBlockView codeBlockView;
    private WebView webView;
    private FrameLayout body;

    private String type = "html";
    private String code = "";
    private String language = "";

    public ArtifactView(Context context) {
        super(context);
        init();
    }

    @SuppressLint({"SetJavaScriptEnabled", "SetTextI18n"})
    private void init() {
        setBackgroundColor(0xFF181825);
        int pad = dp(6);
        setPadding(pad, pad, pad, pad);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Tab bar
        LinearLayout tabBar = new LinearLayout(getContext());
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);

        tabCode = makeTab("Code", true);
        tabPreview = makeTab("Preview", false);
        tabBar.addView(tabCode);
        tabBar.addView(tabPreview);

        View spacer = new View(getContext());
        LinearLayout.LayoutParams spacerLp =
                new LinearLayout.LayoutParams(0, 1, 1f);
        tabBar.addView(spacer, spacerLp);

        expandButton = new Button(getContext());
        expandButton.setText("⛶");
        expandButton.setAllCaps(false);
        expandButton.setTextColor(Color.WHITE);
        expandButton.setBackgroundColor(0xFF313244);
        expandButton.setPadding(dp(10), dp(2), dp(10), dp(2));
        expandButton.setOnClickListener(v -> toggleFullscreen());
        tabBar.addView(expandButton);

        root.addView(tabBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Body container
        body = new FrameLayout(getContext());
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        bodyLp.topMargin = dp(6);
        root.addView(body, bodyLp);

        codeBlockView = new CodeBlockView(getContext());
        webView = createSecureWebView();

        body.addView(codeBlockView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        body.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        tabCode.setOnClickListener(v -> showTab(true));
        tabPreview.setOnClickListener(v -> showTab(false));
        showTab(true);
    }

    private Button makeTab(String label, boolean active) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(active ? 0xFF45475A : 0xFF313244);
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createSecureWebView() {
        WebView wv = new WebView(getContext());
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setAllowFileAccess(false);
        wv.getSettings().setAllowContentAccess(false);
        wv.getSettings().setAllowFileAccessFromFileURLs(false);
        wv.getSettings().setAllowUniversalAccessFromFileURLs(false);
        wv.getSettings().setDomStorageEnabled(true);
        wv.setBackgroundColor(Color.WHITE);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Block ALL navigation attempts. Initial load uses loadDataWithBaseURL only.
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        return wv;
    }

    private void showTab(boolean codeTab) {
        tabCode.setBackgroundColor(codeTab ? 0xFF45475A : 0xFF313244);
        tabPreview.setBackgroundColor(codeTab ? 0xFF313244 : 0xFF45475A);
        codeBlockView.setVisibility(codeTab ? VISIBLE : GONE);
        webView.setVisibility(codeTab ? GONE : VISIBLE);
    }

    private boolean fullscreen = false;
    private int savedHeight = 0;

    private void toggleFullscreen() {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) body.getLayoutParams();
        if (!fullscreen) {
            savedHeight = lp.height;
            lp.height = dp(600);
        } else {
            lp.height = savedHeight > 0 ? savedHeight : dp(320);
        }
        body.setLayoutParams(lp);
        fullscreen = !fullscreen;
    }

    /**
     * Set the artifact content.
     * @param type one of "html", "svg", "mermaid", "react"
     */
    public void setArtifact(String type, String language, String code) {
        this.type = type == null ? "html" : type.toLowerCase();
        this.language = language == null ? "" : language;
        this.code = code == null ? "" : code;

        codeBlockView.setCode(this.language.isEmpty() ? this.type : this.language, this.code);

        String html = buildHtmlForType(this.type, this.code);
        webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null);
    }

    private String buildHtmlForType(String type, String code) {
        switch (type) {
            case "mermaid":
                return mermaidTemplate(code);
            case "react":
            case "jsx":
                return reactTemplate(code);
            case "svg":
                return svgTemplate(code);
            case "html":
            default:
                return code; // raw HTML as-is
        }
    }

    private String mermaidTemplate(String diagram) {
        // TODO: inline mermaid.min.js into assets to remove online dependency.
        String safe = diagram.replace("</", "<\\/");
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<style>body{margin:0;padding:12px;font-family:sans-serif;}</style>"
                + "<script src='" + MERMAID_CDN + "'></script>"
                + "</head><body>"
                + "<pre class='mermaid'>" + escapeHtml(diagram) + "</pre>"
                + "<script>"
                + "document.addEventListener('DOMContentLoaded',function(){"
                + "  if(window.mermaid){mermaid.initialize({startOnLoad:true});}"
                + "});"
                + "</script>"
                + "<!-- safe: " + safe.substring(0, 0) + " -->"
                + "</body></html>";
    }

    private String reactTemplate(String jsx) {
        // TODO: inline react/react-dom/babel into assets to remove online dependency.
        // Security: escape </script> so agent-supplied JSX cannot break out of
        // the <script type='text/babel'> container. Note that Babel still
        // evaluates the jsx as code — the agent itself is trusted to produce
        // non-malicious JSX. This guard only prevents accidental / crafted
        // breakouts into the host document context.
        String safeJsx = escapeForScriptBody(jsx);
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<style>body{margin:0;padding:12px;font-family:sans-serif;}</style>"
                + "<script src='" + REACT_CDN + "'></script>"
                + "<script src='" + REACTDOM_CDN + "'></script>"
                + "<script src='" + BABEL_CDN + "'></script>"
                + "</head><body>"
                + "<div id='root'></div>"
                + "<script type='text/babel'>\n"
                + safeJsx + "\n"
                + "</script>"
                + "</body></html>";
    }

    private String svgTemplate(String svg) {
        // Security: SVG can contain <script> and on* event handlers. Since the
        // WebView has JS enabled (required for mermaid/react), strip those
        // before embedding so a crafted SVG artifact cannot exfiltrate data or
        // hijack the rendering context.
        String safeSvg = sanitizeSvg(svg);
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<style>body{margin:0;padding:12px;display:flex;justify-content:center;align-items:center;}"
                + "svg{max-width:100%;height:auto;}</style>"
                + "</head><body>" + safeSvg + "</body></html>";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }



    /**
     * Escape a string so that it can be safely embedded inside a &lt;script&gt;
     * tag body without allowing the content to break out via a closing
     * &lt;/script&gt; tag. Does not protect against other XSS vectors.
     */
    private static String escapeForScriptBody(String s) {
        if (s == null) return "";
        return s.replace("</script", "<\\/script").replace("</SCRIPT", "<\\/SCRIPT");
    }

    /**
     * Best-effort sanitize an SVG blob: strip &lt;script&gt; tags and inline
     * on* event handlers. This is not a full SVG sanitizer but removes the
     * obvious injection vectors before embedding the SVG in a WebView that has
     * JavaScript enabled.
     */
    private static String sanitizeSvg(String svg) {
        if (svg == null) return "";
        String out = svg.replaceAll("(?is)<script[^>]*>.*?</script\s*>", "");
        out = out.replaceAll("(?is)<script[^>]*/>", "");
        // strip on*="..." event handlers (with or without quotes)
        out = out.replaceAll("(?i)\s+on[a-z]+\s*=\s*"[^"]*"", "");
        out = out.replaceAll("(?i)\s+on[a-z]+\s*=\s*'[^']*'", "");
        out = out.replaceAll("(?i)\s+on[a-z]+\s*=\s*[^\s>]+", "");
        // neutralize javascript: URLs inside href/xlink:href attrs
        out = out.replaceAll("(?i)(href\s*=\s*")\s*javascript:", "$1about:blank#");
        out = out.replaceAll("(?i)(href\s*=\s*')\s*javascript:", "$1about:blank#");
        return out;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
