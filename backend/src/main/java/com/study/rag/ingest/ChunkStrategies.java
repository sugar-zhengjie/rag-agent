package com.study.rag.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChunkStrategies {

    private ChunkStrategies() {
    }

    public static List<ChunkDraft> retarget(String strategy, List<ChunkDraft> sections, RecursiveTextSplitter splitter) {
        String mode = strategy == null || strategy.isBlank() ? "structured" : strategy.toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "fixed" -> fixed(sections, splitter.targetChars(), splitter.overlapChars());
            case "parent-child", "parent_child" -> parentChild(sections, splitter.targetChars(), splitter.overlapChars(), splitter.minChars());
            case "semantic" -> semantic(sections, splitter.minChars());
            default -> sections;
        };
    }

    public static List<StrategyHit> compare(String text, List<String> evidence) {
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(120, 20, 10);
        List<ChunkDraft> base = List.of(new ChunkDraft(text, null, null, null, 0));
        List<StrategyHit> hits = new ArrayList<>();
        for (String mode : List.of("fixed", "structured", "parent-child", "semantic")) {
            List<ChunkDraft> chunks = retarget(mode, base, splitter);
            int found = 0;
            List<String> expected = evidence == null ? List.of() : evidence;
            for (String snippet : expected) {
                if (chunks.stream().anyMatch(chunk -> contains(chunk, snippet))) {
                    found++;
                }
            }
            hits.add(new StrategyHit(mode, chunks.size(), expected.isEmpty() ? 1d : (double) found / expected.size()));
        }
        return hits;
    }

    private static List<ChunkDraft> fixed(List<ChunkDraft> sections, int target, int overlap) {
        List<ChunkDraft> chunks = new ArrayList<>();
        int step = Math.max(1, target - Math.max(0, overlap));
        for (ChunkDraft section : sections) {
            String text = section.content() == null ? "" : section.content();
            if (text.length() <= target) {
                chunks.add(copy(section, text, null));
                continue;
            }
            for (int start = 0; start < text.length(); start += step) {
                int end = Math.min(text.length(), start + target);
                chunks.add(copy(section, text.substring(start, end), null));
                if (end == text.length()) {
                    break;
                }
            }
        }
        return chunks;
    }

    private static List<ChunkDraft> parentChild(List<ChunkDraft> sections, int target, int overlap, int minChars) {
        int childTarget = Math.max(80, target / 3);
        int childOverlap = Math.min(overlap, Math.max(0, childTarget / 4));
        if (childOverlap >= childTarget) {
            childOverlap = 0;
        }
        List<ChunkDraft> children = new ArrayList<>();
        for (ChunkDraft section : sections) {
            for (ChunkDraft child : fixed(List.of(section), childTarget, childOverlap)) {
                if (child.content() != null && child.content().length() >= minChars) {
                    children.add(copy(child, child.content(), section.content()));
                }
            }
        }
        return children;
    }

    private static List<ChunkDraft> semantic(List<ChunkDraft> sections, int minChars) {
        List<ChunkDraft> chunks = new ArrayList<>();
        for (ChunkDraft section : sections) {
            List<String> sentences = sentences(section.content());
            StringBuilder group = new StringBuilder();
            String previous = "";
            for (String sentence : sentences) {
                if (!group.isEmpty() && !previous.isBlank() && similarity(previous, sentence) < 0.08 && group.length() >= minChars) {
                    chunks.add(copy(section, group.toString().trim(), null));
                    group.setLength(0);
                }
                if (!group.isEmpty()) {
                    group.append('\n');
                }
                group.append(sentence);
                previous = sentence;
            }
            if (!group.isEmpty()) {
                chunks.add(copy(section, group.toString().trim(), null));
            }
        }
        return chunks;
    }

    private static List<String> sentences(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            current.append(ch);
            if (ch == '。' || ch == '！' || ch == '？' || ch == '\n') {
                String sentence = current.toString().trim();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            sentences.add(current.toString().trim());
        }
        return sentences;
    }

    private static double similarity(String left, String right) {
        String a = left.replaceAll("\\s+", "");
        String b = right.replaceAll("\\s+", "");
        if (a.length() < 2 || b.length() < 2) {
            return 0;
        }
        java.util.Set<String> leftGrams = grams(a);
        java.util.Set<String> rightGrams = grams(b);
        int shared = 0;
        for (String gram : leftGrams) {
            if (rightGrams.contains(gram)) {
                shared++;
            }
        }
        int union = leftGrams.size() + rightGrams.size() - shared;
        return union == 0 ? 0 : (double) shared / union;
    }

    private static java.util.Set<String> grams(String text) {
        java.util.Set<String> grams = new java.util.LinkedHashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            grams.add(text.substring(i, i + 2));
        }
        return grams;
    }

    private static boolean contains(ChunkDraft chunk, String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return false;
        }
        String content = chunk.content() == null ? "" : chunk.content();
        String parent = chunk.parentContent() == null ? "" : chunk.parentContent();
        return content.contains(snippet) || parent.contains(snippet);
    }

    private static ChunkDraft copy(ChunkDraft source, String content, String parent) {
        return new ChunkDraft(content, source.page(), source.sheet(), source.heading(), 0, parent);
    }

    public record StrategyHit(String strategy, int chunks, double evidenceRecall) {
    }
}
