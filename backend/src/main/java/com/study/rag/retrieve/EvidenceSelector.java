package com.study.rag.retrieve;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EvidenceSelector {

    private static final Pattern SENTENCE = Pattern.compile("[^。！？；\\n]+[。！？；\\n]?");
    private static final Set<String> STOP_CHARS = setOf("的了吗呢吧啊是在和与或及被把将就也都还又很最这那你我他她它需要什么怎么如何哪些是否可以请问一下一个有没不");

    private EvidenceSelector() {
    }

    public static List<RankedChunk> dedup(List<RankedChunk> chunks, double jaccardThreshold) {
        List<RankedChunk> sorted = new ArrayList<>(chunks);
        sorted.sort((left, right) -> Double.compare(right.score(), left.score()));
        List<RankedChunk> kept = new ArrayList<>();
        List<Set<String>> grams = new ArrayList<>();
        for (RankedChunk chunk : sorted) {
            String normalized = normalize(chunk.content());
            if (normalized.isEmpty()) {
                continue;
            }
            Set<String> current = bigrams(normalized);
            boolean duplicate = false;
            for (int i = 0; i < kept.size(); i++) {
                String previous = normalize(kept.get(i).content());
                if (previous.contains(normalized) || normalized.contains(previous) || jaccard(grams.get(i), current) >= jaccardThreshold) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(chunk);
                grams.add(current);
            }
        }
        return kept;
    }

    public static String select(String content, String query, int neighborSentences) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> sentences = sentences(content);
        Set<Integer> hits = new LinkedHashSet<>();
        List<String> terms = terms(query);
        for (int i = 0; i < sentences.size(); i++) {
            if (!relevant(sentences.get(i), terms)) {
                continue;
            }
            int from = Math.max(0, i - Math.max(0, neighborSentences));
            for (int j = from; j <= i; j++) {
                hits.add(j);
            }
        }
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder evidence = new StringBuilder();
        for (int index : hits) {
            if (!evidence.isEmpty()) {
                evidence.append('\n');
            }
            evidence.append(sentences.get(index).trim());
        }
        return evidence.toString().trim();
    }

    static List<String> terms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        StringBuilder current = new StringBuilder();
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                current.append(ch);
                continue;
            }
            addTerms(terms, current.toString());
            current.setLength(0);
        }
        addTerms(terms, current.toString());
        return terms;
    }

    private static void addTerms(List<String> terms, String token) {
        if (token.isBlank()) {
            return;
        }
        if (token.chars().allMatch(ch -> ch < 128)) {
            if (token.length() >= 2) {
                terms.add(token.toLowerCase());
            }
            return;
        }
        String compact = stripStops(token);
        if (compact.length() < 2) {
            return;
        }
        if (compact.length() == 2) {
            terms.add(compact);
            return;
        }
        for (int i = 0; i < compact.length() - 1; i++) {
            terms.add(compact.substring(i, i + 2));
        }
    }

    private static String stripStops(String token) {
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            String ch = token.substring(i, i + 1);
            if (!STOP_CHARS.contains(ch)) {
                kept.append(ch);
            }
        }
        return kept.toString();
    }

    private static boolean relevant(String sentence, List<String> terms) {
        String normalized = normalize(sentence).toLowerCase();
        for (String term : terms) {
            if (term.length() >= 2 && normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sentences(String content) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(content.replace("\r\n", "\n"));
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "");
    }

    private static Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        if (text.length() < 2) {
            grams.add(text);
            return grams;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            grams.add(text.substring(i, i + 2));
        }
        return grams;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String gram : left) {
            if (right.contains(gram)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    private static Set<String> setOf(String chars) {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < chars.length(); i++) {
            values.add(chars.substring(i, i + 1));
        }
        return values;
    }
}
