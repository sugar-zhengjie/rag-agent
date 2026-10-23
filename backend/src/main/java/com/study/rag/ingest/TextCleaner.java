package com.study.rag.ingest;

import java.util.regex.Pattern;

public final class TextCleaner {

    public static final String VERSION = "clean-v1";

    private static final Pattern NOISE_LINE = Pattern.compile("^(?:第\\s*\\d+\\s*页|\\d+\\s*/\\s*\\d+|-\\s*\\d+\\s*-|—\\s*\\d+\\s*—)$");

    private TextCleaner() {
    }

    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        StringBuilder kept = new StringBuilder();
        for (String raw : normalized.split("\n", -1)) {
            String line = stripControls(raw).strip();
            if (line.isEmpty()) {
                if (!kept.isEmpty() && kept.charAt(kept.length() - 1) != '\n') {
                    kept.append('\n');
                }
                continue;
            }
            if (NOISE_LINE.matcher(line).matches()) {
                continue;
            }
            if (!kept.isEmpty() && kept.charAt(kept.length() - 1) != '\n') {
                kept.append('\n');
            }
            kept.append(line);
        }
        return kept.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static String stripControls(String line) {
        StringBuilder cleaned = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\t') {
                cleaned.append(' ');
            } else if (!Character.isISOControl(ch)) {
                cleaned.append(ch);
            }
        }
        return cleaned.toString().replaceAll(" {2,}", " ");
    }
}
