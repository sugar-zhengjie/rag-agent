package com.study.rag.retrieve;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EvidenceOrganizer {

    public static final double DEFAULT_HIGH_RATIO = 0.72;

    private static final Pattern VERSION = Pattern.compile(
            "(?i)(?:版本|version)\\s*[:：]?\\s*([A-Za-z0-9._-]{1,24})|(?<![A-Za-z0-9])v\\d+(?:\\.\\d+){0,2}|20\\d{2}版");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private EvidenceOrganizer() {
    }

    public enum Layout {
        RELEVANCE("按相关度"),
        COMPARE("按主题"),
        TIMELINE("按时间线"),
        TROUBLESHOOT("按排障结构");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static Layout detect(String query) {
        String text = query == null ? "" : query;
        if (containsAny(text, "对比", "比较", "区别", "差异")) {
            return Layout.COMPARE;
        }
        if (containsAny(text, "时间线", "演变", "历史", "先后", "哪年", "何时", "版本变化", "按时间")) {
            return Layout.TIMELINE;
        }
        if (containsAny(text, "故障", "报错", "排查", "怎么办", "失败")) {
            return Layout.TROUBLESHOOT;
        }
        return Layout.RELEVANCE;
    }

    public static List<RankedChunk> organize(String query, List<RankedChunk> chunks) {
        return organize(query, chunks, DEFAULT_HIGH_RATIO);
    }

    public static List<RankedChunk> organize(String query, List<RankedChunk> chunks, double highRatio) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<RankedChunk> prepared = chunks.stream().map(EvidenceOrganizer::ensureVersion).toList();
        double top = prepared.stream().mapToDouble(RankedChunk::score).max().orElse(0);
        double cutoff = top * highRatio;
        List<RankedChunk> high = new ArrayList<>();
        List<RankedChunk> low = new ArrayList<>();
        for (RankedChunk chunk : prepared) {
            if (chunk.score() >= cutoff) {
                high.add(chunk);
            } else {
                low.add(chunk);
            }
        }
        Layout layout = detect(query);
        List<RankedChunk> ordered = new ArrayList<>();
        ordered.addAll(arrange(layout, high, false));
        ordered.addAll(arrange(layout, low, true));
        return annotateConflicts(ordered);
    }

    static String versionOf(String fileName, String content) {
        String fromName = firstVersion(fileName);
        if (fromName != null) {
            return fromName;
        }
        String sample = content == null ? "" : content.substring(0, Math.min(content.length(), 300));
        return firstVersion(sample);
    }

    private static RankedChunk ensureVersion(RankedChunk chunk) {
        if (chunk.version() != null && !chunk.version().isBlank()) {
            return chunk;
        }
        return chunk.withMeta(chunk.createdAt(), versionOf(chunk.fileName(), chunk.content()));
    }

    private static List<RankedChunk> arrange(Layout layout, List<RankedChunk> chunks, boolean low) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        return switch (layout) {
            case COMPARE -> byTopic(chunks, low);
            case TIMELINE -> byTime(chunks, low);
            case TROUBLESHOOT -> byTroubleshooting(chunks, low);
            case RELEVANCE -> byDocument(chunks, low, null);
        };
    }

    private static List<RankedChunk> byDocument(List<RankedChunk> chunks, boolean low, String sectionOverride) {
        Map<String, List<RankedChunk>> groups = new LinkedHashMap<>();
        for (RankedChunk chunk : chunks) {
            groups.computeIfAbsent(chunk.docId() == null ? "" : chunk.docId(), key -> new ArrayList<>()).add(chunk);
        }
        List<List<RankedChunk>> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparingDouble(EvidenceOrganizer::maxScore).reversed());
        List<RankedChunk> result = new ArrayList<>();
        for (List<RankedChunk> group : ordered) {
            group.sort(Comparator.comparingInt(RankedChunk::chunkIndex));
            String fileName = group.get(0).fileName() == null ? "未命名" : group.get(0).fileName();
            String section = sectionOverride == null
                    ? (low ? "低置信度 · " : "高置信度 · ") + fileName
                    : sectionOverride;
            for (RankedChunk chunk : group) {
                result.add(chunk.withOrganization(section, null));
            }
        }
        return result;
    }

    private static List<RankedChunk> byTopic(List<RankedChunk> chunks, boolean low) {
        Map<String, List<RankedChunk>> topics = new LinkedHashMap<>();
        for (RankedChunk chunk : chunks) {
            String topic = chunk.heading() == null || chunk.heading().isBlank() ? "未分主题" : chunk.heading();
            topics.computeIfAbsent(topic, key -> new ArrayList<>()).add(chunk);
        }
        List<Map.Entry<String, List<RankedChunk>>> ordered = new ArrayList<>(topics.entrySet());
        ordered.sort(Comparator.comparingDouble((Map.Entry<String, List<RankedChunk>> entry) -> maxScore(entry.getValue())).reversed());
        List<RankedChunk> result = new ArrayList<>();
        for (Map.Entry<String, List<RankedChunk>> entry : ordered) {
            String section = (low ? "低置信度 · " : "") + "主题：" + entry.getKey();
            result.addAll(byDocument(entry.getValue(), low, section));
        }
        return result;
    }

    private static List<RankedChunk> byTime(List<RankedChunk> chunks, boolean low) {
        List<RankedChunk> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparingLong(EvidenceOrganizer::timeKey)
                .thenComparing(chunk -> chunk.docId() == null ? "" : chunk.docId())
                .thenComparingInt(RankedChunk::chunkIndex));
        String section = low ? "低置信度 · 时间线" : "时间线";
        List<RankedChunk> result = new ArrayList<>();
        for (RankedChunk chunk : sorted) {
            result.add(chunk.withOrganization(section, null));
        }
        return result;
    }

    private static List<RankedChunk> byTroubleshooting(List<RankedChunk> chunks, boolean low) {
        List<String> order = List.of("现象", "原因", "处理步骤", "注意事项", "其他");
        Map<String, List<RankedChunk>> buckets = new LinkedHashMap<>();
        for (String name : order) {
            buckets.put(name, new ArrayList<>());
        }
        for (RankedChunk chunk : chunks) {
            buckets.get(troubleshootingBucket(chunk.content())).add(chunk);
        }
        List<RankedChunk> result = new ArrayList<>();
        for (String name : order) {
            List<RankedChunk> bucket = buckets.get(name);
            if (bucket.isEmpty()) {
                continue;
            }
            String section = (low ? "低置信度 · " : "") + name;
            result.addAll(byDocument(bucket, low, section));
        }
        return result;
    }

    static String troubleshootingBucket(String content) {
        String text = content == null ? "" : content;
        int phenomenon = earliest(text, "现象", "报错", "错误", "失败", "无法", "异常");
        int cause = earliest(text, "原因", "因为", "由于", "导致");
        int steps = earliest(text, "处理", "步骤", "解决", "修复", "操作");
        int caution = earliest(text, "注意", "避免", "不要", "禁止");
        int best = Integer.MAX_VALUE;
        String bucket = "其他";
        if (phenomenon < best) {
            best = phenomenon;
            bucket = "现象";
        }
        if (cause < best) {
            best = cause;
            bucket = "原因";
        }
        if (steps < best) {
            best = steps;
            bucket = "处理步骤";
        }
        if (caution < best) {
            bucket = "注意事项";
        }
        return bucket;
    }

    private static List<RankedChunk> annotateConflicts(List<RankedChunk> chunks) {
        List<RankedChunk> result = new ArrayList<>(chunks);
        for (int i = 0; i < result.size(); i++) {
            if (low(result.get(i))) {
                continue;
            }
            for (int j = i + 1; j < result.size(); j++) {
                if (low(result.get(j)) || !contradicts(result.get(i), result.get(j))) {
                    continue;
                }
                String note = conflictNote(result.get(i), result.get(j));
                result.set(i, withConflict(result.get(i), note));
                result.set(j, withConflict(result.get(j), note));
            }
        }
        return result;
    }

    private static boolean contradicts(RankedChunk left, RankedChunk right) {
        if (left.docId() != null && left.docId().equals(right.docId())) {
            return false;
        }
        if (!sameTopic(left, right)) {
            return false;
        }
        if (numberConflict(left.content(), right.content())) {
            return true;
        }
        return polarity(left.content()) * polarity(right.content()) < 0;
    }

    private static boolean numberConflict(String left, String right) {
        List<String> leftNumbers = numbers(left);
        List<String> rightNumbers = numbers(right);
        if (leftNumbers.isEmpty() || rightNumbers.isEmpty()) {
            return false;
        }
        for (String value : leftNumbers) {
            if (rightNumbers.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTopic(RankedChunk left, RankedChunk right) {
        return left.heading() != null && !left.heading().isBlank() && left.heading().equals(right.heading());
    }

    private static String conflictNote(RankedChunk left, RankedChunk right) {
        return "可能冲突。版本：" + label(left.version()) + " / " + label(right.version())
                + "。入库时间：" + timeText(left.createdAt()) + " / " + timeText(right.createdAt());
    }

    private static RankedChunk withConflict(RankedChunk chunk, String note) {
        if (chunk.conflictNote() == null || chunk.conflictNote().isBlank()) {
            return chunk.withOrganization(chunk.section(), note);
        }
        if (chunk.conflictNote().contains(note)) {
            return chunk;
        }
        return chunk.withOrganization(chunk.section(), chunk.conflictNote() + "；" + note);
    }

    private static boolean low(RankedChunk chunk) {
        return chunk.section() != null && chunk.section().startsWith("低置信度");
    }

    private static int polarity(String content) {
        String text = content == null ? "" : content;
        boolean negative = containsAny(text, "禁止", "不要", "不能", "不允许", "未开放");
        boolean positive = containsAny(text, "允许", "可以", "需要", "应当");
        if (negative && !positive) {
            return -1;
        }
        if (positive && !negative) {
            return 1;
        }
        return 0;
    }

    private static List<String> numbers(String content) {
        if (content == null) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(content);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

    private static long timeKey(RankedChunk chunk) {
        Matcher matcher = Pattern.compile("(20\\d{2})(?:[-/.年](\\d{1,2}))?").matcher(chunk.content() == null ? "" : chunk.content());
        if (matcher.find()) {
            String month = matcher.group(2) == null ? "01" : matcher.group(2);
            if (month.length() == 1) {
                month = "0" + month;
            }
            return Long.parseLong(matcher.group(1) + month);
        }
        return chunk.createdAt() > 0 ? chunk.createdAt() : Long.MAX_VALUE;
    }

    private static double maxScore(List<RankedChunk> chunks) {
        return chunks.stream().mapToDouble(RankedChunk::score).max().orElse(0);
    }

    private static int earliest(String text, String... keys) {
        int best = Integer.MAX_VALUE;
        for (String key : keys) {
            int index = text.indexOf(key);
            if (index >= 0 && index < best) {
                best = index;
            }
        }
        return best;
    }

    private static String firstVersion(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String captured = matcher.group(1);
        return captured == null ? matcher.group() : captured;
    }

    public static String formatTime(long createdAt) {
        return timeText(createdAt);
    }

    private static String timeText(long createdAt) {
        if (createdAt <= 0) {
            return "未知";
        }
        return TIME.format(Instant.ofEpochMilli(createdAt));
    }

    private static String label(String version) {
        return version == null || version.isBlank() ? "未标注" : version;
    }

    private static boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
