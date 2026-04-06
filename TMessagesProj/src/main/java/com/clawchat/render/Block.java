package com.clawchat.render;

/**
 * Sealed-style base class for a renderable message block.
 * Subclasses are the only allowed variants: TextBlock, CodeBlock, ArtifactBlock.
 */
public abstract class Block {

    private Block() {}

    /** Plain GFM markdown text. */
    public static final class TextBlock extends Block {
        public final String markdown;
        public TextBlock(String markdown) {
            this.markdown = markdown == null ? "" : markdown;
        }
    }

    /** Syntax-highlighted code block (non-artifact). */
    public static final class CodeBlock extends Block {
        public final String language;
        public final String code;
        public CodeBlock(String language, String code) {
            this.language = language == null ? "" : language;
            this.code = code == null ? "" : code;
        }
    }

    /**
     * Rich WebView-rendered artifact.
     * type ∈ {"html", "svg", "mermaid", "react"}.
     */
    public static final class ArtifactBlock extends Block {
        public final String type;
        public final String language;
        public final String code;
        public final String title;
        public ArtifactBlock(String type, String language, String code, String title) {
            this.type = type == null ? "html" : type;
            this.language = language == null ? "" : language;
            this.code = code == null ? "" : code;
            this.title = title == null ? "" : title;
        }
    }
}
