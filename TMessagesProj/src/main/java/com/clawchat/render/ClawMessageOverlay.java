package com.clawchat.render;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Top-level renderer for a single agent-AI message body.
 *
 * Parses markdown via {@link MarkdownParser} and creates one child view per block:
 *   - TextBlock     → TextView rendered by {@link MarkdownRenderer}
 *   - CodeBlock     → {@link CodeBlockView}
 *   - ArtifactBlock → {@link ArtifactView}
 *
 * Streaming-friendly: {@link #clearAndSetMessage(String)} fully re-renders from scratch.
 */
public class ClawMessageOverlay extends LinearLayout {

    private final MarkdownRenderer markdownRenderer;

    public ClawMessageOverlay(Context context) {
        super(context);
        setOrientation(VERTICAL);
        this.markdownRenderer = new MarkdownRenderer(context);
    }

    public void setMessage(String body) {
        removeAllViews();
        if (body == null) return;
        List<Block> blocks = MarkdownParser.parse(body);
        for (Block b : blocks) {
            addView(viewForBlock(b));
        }
    }

    /** Alias for incremental/streaming updates — same semantics as setMessage today. */
    public void clearAndSetMessage(String body) {
        setMessage(body);
    }

    private android.view.View viewForBlock(Block b) {
        if (b instanceof Block.TextBlock) {
            TextView tv = new TextView(getContext());
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            int pad = dp(4);
            lp.setMargins(pad, pad, pad, pad);
            tv.setLayoutParams(lp);
            markdownRenderer.renderInto(tv, ((Block.TextBlock) b).markdown);
            return tv;
        }
        if (b instanceof Block.CodeBlock) {
            Block.CodeBlock cb = (Block.CodeBlock) b;
            CodeBlockView v = new CodeBlockView(getContext());
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            int pad = dp(4);
            lp.setMargins(pad, pad, pad, pad);
            v.setLayoutParams(lp);
            v.setCode(cb.language, cb.code);
            return v;
        }
        if (b instanceof Block.ArtifactBlock) {
            Block.ArtifactBlock ab = (Block.ArtifactBlock) b;
            ArtifactView v = new ArtifactView(getContext());
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            int pad = dp(4);
            lp.setMargins(pad, pad, pad, pad);
            v.setLayoutParams(lp);
            v.setArtifact(ab.type, ab.language, ab.code);
            return v;
        }
        // Unknown — fallback empty text.
        return new TextView(getContext());
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
