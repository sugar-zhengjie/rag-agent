package com.study.rag.ingest;

import java.util.ArrayList;
import java.util.List;

public class MarkdownChunker {

    private final RecursiveTextSplitter splitter;

    public MarkdownChunker(RecursiveTextSplitter splitter) {
        this.splitter = splitter;
    }

    public List<ChunkDraft> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> headingStack = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String currentHeading = "";
        boolean inFence = false;
        List<ChunkDraft> drafts = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                appendLine(body, line);
                continue;
            }
            Heading heading = inFence ? null : parseHeading(trimmed);
            if (heading == null) {
                appendLine(body, line);
                continue;
            }
            flush(drafts, currentHeading, body);
            while (headingStack.size() >= heading.level()) {
                headingStack.remove(headingStack.size() - 1);
            }
            while (headingStack.size() < heading.level() - 1) {
                headingStack.add("");
            }
            headingStack.add(heading.title());
            currentHeading = joinHeading(headingStack);
        }
        flush(drafts, currentHeading, body);
        return drafts;
    }

    private void flush(List<ChunkDraft> drafts, String heading, StringBuilder body) {
        String text = body.toString().trim();
        body.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        String prefixed = heading == null || heading.isBlank() ? text : heading + "\n" + text;
        for (String piece : splitter.split(prefixed)) {
            drafts.add(new ChunkDraft(piece, null, null, blankToNull(heading), drafts.size()));
        }
    }

    private static void appendLine(StringBuilder body, String line) {
        if (!body.isEmpty()) {
            body.append('\n');
        }
        body.append(line);
    }

    private static String joinHeading(List<String> stack) {
        StringBuilder path = new StringBuilder();
        for (String part : stack) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append(" / ");
            }
            path.append(part);
        }
        return path.toString();
    }

    private static Heading parseHeading(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level >= line.length() || line.charAt(level) != ' ') {
            return null;
        }
        String title = line.substring(level + 1).trim();
        if (title.isEmpty()) {
            return null;
        }
        return new Heading(level, title);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Heading(int level, String title) {
    }
}
