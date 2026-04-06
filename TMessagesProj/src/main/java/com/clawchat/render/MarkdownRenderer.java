// DEPENDENCY: io.noties.markwon:core:4.6.2
// DEPENDENCY: io.noties.markwon:ext-tables:4.6.2
// DEPENDENCY: io.noties.markwon:ext-strikethrough:4.6.2
// DEPENDENCY: io.noties.prism4j:prism4j:2.0.0
// DEPENDENCY: io.noties.prism4j:prism4j-bundler:2.0.0
package com.clawchat.render;

import android.content.Context;
import android.widget.TextView;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;

/**
 * Thin wrapper around Markwon for rendering GFM markdown into a TextView.
 *
 * - Tables + strikethrough enabled.
 * - Images NOT loaded (no image plugin installed).
 * - CJK-friendly line height via lineSpacingMultiplier = 1.6.
 */
public final class MarkdownRenderer {

    private static final float CJK_LINE_SPACING = 1.6f;

    private final Markwon markwon;

    public MarkdownRenderer(Context context) {
        this.markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .build();
    }

    /** Render the given markdown into the TextView, applying CJK line spacing. */
    public void renderInto(TextView target, String markdown) {
        if (target == null) return;
        target.setLineSpacing(0f, CJK_LINE_SPACING);
        markwon.setMarkdown(target, markdown == null ? "" : markdown);
    }

    public Markwon markwon() {
        return markwon;
    }
}
