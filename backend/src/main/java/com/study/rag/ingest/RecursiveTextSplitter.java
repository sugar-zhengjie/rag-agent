package com.study.rag.ingest;

import java.util.ArrayList;
import java.util.List;

public class RecursiveTextSplitter {

    private static final List<String> SEPARATORS = List.of("\n\n", "\n", "。", "！", "？", "；", ".", "!", "?", ";", " ");

    private final int targetChars;
    private final int overlapChars;
    private final int minChars;

    public RecursiveTextSplitter(int targetChars, int overlapChars, int minChars) {
        if (targetChars <= 0) {
            throw new IllegalArgumentException("targetChars 必须大于 0");
        }
        if (overlapChars < 0 || overlapChars >= targetChars) {
            throw new IllegalArgumentException("overlapChars 必须大于等于 0 且小于 targetChars");
        }
        if (minChars < 0) {
            throw new IllegalArgumentException("minChars 不能为负");
        }
        this.targetChars = targetChars;
        this.overlapChars = overlapChars;
        this.minChars = minChars;
    }

    public int targetChars() {
        return targetChars;
    }

    public int overlapChars() {
        return overlapChars;
    }

    public int minChars() {
        return minChars;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> raw = new ArrayList<>();
        split(normalized, 0, raw);
        List<String> chunks = new ArrayList<>();
        for (String piece : raw) {
            String trimmed = piece.trim();
            if (trimmed.length() >= minChars) {
                chunks.add(trimmed);
            }
        }
        return chunks;
    }

    private void split(String text, int separatorIndex, List<String> output) {
        if (text.length() <= targetChars) {
            output.add(text);
            return;
        }
        if (separatorIndex >= SEPARATORS.size()) {
            hardWindow(text, output);
            return;
        }
        String separator = SEPARATORS.get(separatorIndex);
        List<String> parts = splitBy(text, separator);
        if (parts.size() <= 1) {
            split(text, separatorIndex + 1, output);
            return;
        }
        output.addAll(merge(parts, separator, separatorIndex));
    }

    private List<String> merge(List<String> parts, String separator, int separatorIndex) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.length() > targetChars) {
                flush(chunks, current);
                split(part, separatorIndex + 1, chunks);
                continue;
            }
            if (current.isEmpty()) {
                current.append(part);
                continue;
            }
            if (current.length() + separator.length() + part.length() <= targetChars) {
                current.append(separator).append(part);
                continue;
            }
            String finished = current.toString();
            chunks.add(finished);
            current.setLength(0);
            String overlap = tail(finished, overlapChars);
            if (!overlap.isEmpty() && overlap.length() + separator.length() + part.length() <= targetChars) {
                current.append(overlap).append(separator).append(part);
            } else {
                current.append(part);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    private void hardWindow(String text, List<String> output) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + targetChars);
            output.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            int next = end - overlapChars;
            start = next > start ? next : end;
        }
    }

    private static List<String> splitBy(String text, String separator) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        int index;
        while ((index = text.indexOf(separator, from)) >= 0) {
            String part = text.substring(from, index).trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            from = index + separator.length();
        }
        String tail = text.substring(from).trim();
        if (!tail.isEmpty()) {
            parts.add(tail);
        }
        return parts;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private static String tail(String text, int chars) {
        if (chars <= 0 || text.isEmpty()) {
            return "";
        }
        return text.substring(Math.max(0, text.length() - chars));
    }
}
