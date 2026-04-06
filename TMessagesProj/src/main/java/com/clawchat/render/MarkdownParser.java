package com.clawchat.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Splits a markdown message body into a list of Blocks.
 *
 * Rules:
 *   - ```lang ... ```  fenced code blocks are extracted.
 *   - If lang ∈ {html, svg, mermaid, jsx} → ArtifactBlock (artifact type = lang, jsx → "react").
 *   - Otherwise → CodeBlock.
 *   - Everything between fences → TextBlock (empty/whitespace-only skipped).
 *
 * Also supports an explicit marker: ```lang artifact  which forces Artifact treatment
 * regardless of language.
 */
public final class MarkdownParser {

    private static final Set<String> ARTIFACT_LANGS = new HashSet<>(Arrays.asList(
            "html", "svg", "mermaid", "jsx"
    ));

    private MarkdownParser() {}

    public static List<Block> parse(String body) {
        List<Block> out = new ArrayList<>();
        if (body == null || body.isEmpty()) return out;

        // Normalize line endings.
        String src = body.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = src.split("\n", -1);

        StringBuilder textBuf = new StringBuilder();
        boolean inFence = false;
        String fenceLang = "";
        boolean fenceArtifactMarker = false;
        StringBuilder codeBuf = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!inFence) {
                if (trimmed.startsWith("```")) {
                    // flush text block
                    flushText(out, textBuf);
                    textBuf.setLength(0);

                    String info = trimmed.substring(3).trim();
                    // Support "```lang artifact" or just "```lang"
                    fenceArtifactMarker = false;
                    if (info.toLowerCase(Locale.ROOT).endsWith(" artifact")) {
                        fenceArtifactMarker = true;
                        info = info.substring(0, info.length() - " artifact".length()).trim();
                    }
                    fenceLang = info.toLowerCase(Locale.ROOT);
                    inFence = true;
                    codeBuf.setLength(0);
                } else {
                    textBuf.append(line);
                    if (i < lines.length - 1) textBuf.append('\n');
                }
            } else {
                if (trimmed.startsWith("```")) {
                    // closing fence
                    String code = codeBuf.toString();
                    // strip trailing newline if any
                    if (code.endsWith("\n")) code = code.substring(0, code.length() - 1);

                    boolean isArtifact = fenceArtifactMarker || ARTIFACT_LANGS.contains(fenceLang);
                    if (isArtifact) {
                        String type;
                        if ("jsx".equals(fenceLang)) type = "react";
                        else if (fenceLang.isEmpty()) type = "html";
                        else type = fenceLang;
                        out.add(new Block.ArtifactBlock(type, fenceLang, code, ""));
                    } else {
                        out.add(new Block.CodeBlock(fenceLang, code));
                    }
                    inFence = false;
                    fenceLang = "";
                    fenceArtifactMarker = false;
                    codeBuf.setLength(0);
                } else {
                    codeBuf.append(line).append('\n');
                }
            }
        }

        // Unterminated fence → treat buffered code as CodeBlock.
        if (inFence) {
            String code = codeBuf.toString();
            if (code.endsWith("\n")) code = code.substring(0, code.length() - 1);
            out.add(new Block.CodeBlock(fenceLang, code));
        }

        flushText(out, textBuf);
        return out;
    }

    public static boolean isArtifactLang(String lang) {
        if (lang == null) return false;
        return ARTIFACT_LANGS.contains(lang.toLowerCase(Locale.ROOT));
    }

    private static void flushText(List<Block> out, StringBuilder buf) {
        if (buf.length() == 0) return;
        String s = buf.toString();
        if (s.trim().isEmpty()) return;
        // Trim one leading/trailing newline to tidy spacing around fences.
        if (s.startsWith("\n")) s = s.substring(1);
        if (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        out.add(new Block.TextBlock(s));
    }
}
