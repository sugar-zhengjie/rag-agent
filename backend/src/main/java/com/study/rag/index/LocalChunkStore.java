package com.study.rag.index;

import com.study.rag.acl.PreparedSearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalChunkStore {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final Map<String, StoredDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, ChunkDocument> chunks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> termFrequency = new ConcurrentHashMap<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final Map<String, Integer> lengths = new HashMap<>();
    private int indexed;
    private double lengthSum;

    public synchronized void index(StoredDocument document, List<ChunkDocument> incoming) {
        documents.put(document.getDocId(), document);
        for (ChunkDocument chunk : incoming) {
            removeChunk(chunk.getChunkId());
            chunks.put(chunk.getChunkId(), chunk);
            Map<String, Integer> frequencies = frequencies(chunk.getContent());
            termFrequency.put(chunk.getChunkId(), frequencies);
            int length = frequencies.values().stream().mapToInt(Integer::intValue).sum();
            lengths.put(chunk.getChunkId(), length);
            lengthSum += length;
            indexed++;
            for (String term : frequencies.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
    }

    public List<StoredDocument> listDocuments(List<String> datasetIds) {
        PreparedSearch search = PreparedSearch.prepare(datasetIds);
        if (!search.execute()) {
            return List.of();
        }
        return documents.values().stream()
                .filter(document -> search.datasetIds().contains(document.getDatasetId()))
                .sorted(Comparator.comparingLong(StoredDocument::getCreatedAt).reversed())
                .toList();
    }

    public StoredDocument findDocument(String docId) {
        return documents.get(docId);
    }

    public List<ChunkDocument> listChunks(String docId) {
        return chunks.values().stream()
                .filter(chunk -> docId.equals(chunk.getDocId()))
                .sorted(Comparator.comparingInt(ChunkDocument::getChunkIndex))
                .toList();
    }

    public synchronized boolean deleteDocument(String docId) {
        StoredDocument removed = documents.remove(docId);
        List<String> ids = chunks.values().stream()
                .filter(chunk -> docId.equals(chunk.getDocId()))
                .map(ChunkDocument::getChunkId)
                .toList();
        ids.forEach(this::removeChunk);
        return removed != null || !ids.isEmpty();
    }

    public synchronized List<ChunkHit> searchBm25(String query, int size, List<String> datasetIds) {
        PreparedSearch search = PreparedSearch.prepare(datasetIds);
        if (!search.execute() || indexed == 0) {
            return List.of();
        }
        Map<String, Integer> queryTerms = frequencies(query);
        double average = lengthSum / indexed;
        List<ChunkHit> hits = new ArrayList<>();
        for (ChunkDocument chunk : chunks.values()) {
            if (!search.datasetIds().contains(chunk.getDatasetId())) {
                continue;
            }
            Map<String, Integer> frequencies = termFrequency.getOrDefault(chunk.getChunkId(), Map.of());
            double score = 0;
            int length = lengths.getOrDefault(chunk.getChunkId(), 0);
            for (Map.Entry<String, Integer> term : queryTerms.entrySet()) {
                int df = documentFrequency.getOrDefault(term.getKey(), 0);
                int tf = frequencies.getOrDefault(term.getKey(), 0);
                if (df == 0 || tf == 0) {
                    continue;
                }
                double idf = Math.log(1 + (indexed - df + 0.5) / (df + 0.5));
                double denominator = tf + K1 * (1 - B + B * length / Math.max(average, 1));
                score += idf * (tf * (K1 + 1) / denominator);
            }
            if (score > 0) {
                hits.add(new ChunkHit(chunk, score));
            }
        }
        hits.sort(Comparator.comparingDouble(ChunkHit::score).reversed());
        return hits.size() <= size ? hits : List.copyOf(hits.subList(0, size));
    }

    public synchronized List<ChunkHit> searchKnn(List<Float> vector, int k, List<String> datasetIds) {
        PreparedSearch search = PreparedSearch.prepare(datasetIds);
        if (!search.execute() || vector == null || vector.isEmpty()) {
            return List.of();
        }
        List<ChunkHit> hits = new ArrayList<>();
        for (ChunkDocument chunk : chunks.values()) {
            if (!search.datasetIds().contains(chunk.getDatasetId()) || chunk.getEmbedding() == null) {
                continue;
            }
            double score = cosine(vector, chunk.getEmbedding());
            if (score > 0) {
                hits.add(new ChunkHit(chunk, score));
            }
        }
        hits.sort(Comparator.comparingDouble(ChunkHit::score).reversed());
        return hits.size() <= k ? hits : List.copyOf(hits.subList(0, k));
    }

    private void removeChunk(String chunkId) {
        if (!chunks.containsKey(chunkId)) {
            return;
        }
        chunks.remove(chunkId);
        Map<String, Integer> frequencies = termFrequency.remove(chunkId);
        Integer length = lengths.remove(chunkId);
        if (length != null) {
            lengthSum -= length;
            indexed = Math.max(0, indexed - 1);
        }
        if (frequencies == null) {
            return;
        }
        for (String term : frequencies.keySet()) {
            documentFrequency.computeIfPresent(term, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    static Map<String, Integer> frequencies(String text) {
        Map<String, Integer> frequencies = new HashMap<>();
        if (text == null || text.isBlank()) {
            return frequencies;
        }
        StringBuilder latin = new StringBuilder();
        Character previous = null;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (isCjk(current)) {
                add(frequencies, latin);
                if (previous != null) {
                    frequencies.merge(previous + "" + current, 1, Integer::sum);
                }
                previous = current;
                continue;
            }
            previous = null;
            if (Character.isLetterOrDigit(current) || current == '-' || current == '_') {
                latin.append(Character.toLowerCase(current));
                continue;
            }
            add(frequencies, latin);
        }
        add(frequencies, latin);
        return frequencies;
    }

    private static void add(Map<String, Integer> frequencies, StringBuilder latin) {
        if (latin.length() >= 2) {
            frequencies.merge(latin.toString().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        latin.setLength(0);
    }

    private static boolean isCjk(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    private static double cosine(List<Float> left, List<Float> right) {
        int length = Math.min(left.size(), right.size());
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < length; i++) {
            float a = left.get(i);
            float b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
