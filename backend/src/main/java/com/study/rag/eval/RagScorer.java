package com.study.rag.eval;

import com.study.rag.retrieve.RankedChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RagScorer {

    static final String JUDGE_NOTE = "规则分只说明证据是否被找到、答案里的数字和编码是否能被上下文支撑。它不是事实裁判，也不代替人工复核。抽查时看自动分是否偏向写得长的答案、是否漏掉引错的句子、是否对中文术语不敏感。";

    private static final Pattern ATOM = Pattern.compile("[A-Za-z]{2,}(?:-[A-Za-z0-9]+)+|20\\d{2}-\\d{2}-\\d{2}|20\\d{2}版|\\d+(?:\\.\\d+)?");
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");
    private static final List<String> REFUSAL = List.of("资料中没有", "没有相关", "无法回答", "不能提供", "不予提供", "没有检索到");

    private RagScorer() {
    }

    public static CaseScore score(EvalCase item, List<String> ranked, List<String> context, String answer, int k,
                                  Long retrievalMs, Long rerankMs, Long generationMs) {
        return score(item, ranked, context, ranked, answer, k, retrievalMs, rerankMs, generationMs);
    }

    public static CaseScore score(EvalCase item, List<String> ranked, List<String> context, List<String> metadataTexts,
                                  String answer, int k, Long retrievalMs, Long rerankMs, Long generationMs) {
        List<String> gold = item.goldenContext() == null ? List.of() : item.goldenContext();
        List<String> rankedTexts = ranked == null ? List.of() : ranked;
        List<String> contextTexts = context == null ? rankedTexts : context;
        List<String> filterTexts = metadataTexts == null ? rankedTexts : metadataTexts;
        boolean refuse = "refuse".equals(item.answerType());
        int limit = Math.max(1, k);

        Double hitRate = null;
        Double mrr = null;
        Double contextRecall = null;
        Double contextPrecision = null;
        Double metadataHit = null;
        if (!refuse && !gold.isEmpty()) {
            hitRate = hit(rankedTexts, gold, limit) ? 1d : 0d;
            mrr = reciprocalRank(rankedTexts, gold, limit);
            contextRecall = recall(contextTexts, gold);
            contextPrecision = precision(contextTexts, gold);
            metadataHit = metadataHit(filterTexts, item.metadataFilter(), limit);
        } else if (refuse) {
            contextPrecision = contextTexts.isEmpty() ? 1d : 0d;
        }

        Double faithfulness = null;
        Double answerRelevancy = null;
        Double citationAccuracy = null;
        boolean generationRan = answer != null && !answer.isBlank();
        if (generationRan) {
            faithfulness = faithfulness(answer, contextTexts, refuse);
            answerRelevancy = relevancy(answer, item.goldenAnswer(), refuse);
            citationAccuracy = citations(answer, contextTexts, refuse);
        }

        List<String> retrievalNotes = new ArrayList<>();
        List<String> generationNotes = new ArrayList<>();
        if (hitRate != null && hitRate < 1) {
            retrievalNotes.add("前 " + limit + " 条没有命中应有证据");
        }
        if (contextRecall != null && contextRecall < 1) {
            retrievalNotes.add("回答所需证据没有找全");
        }
        if (contextPrecision != null && contextPrecision < 1) {
            retrievalNotes.add("放入上下文的内容里有和标准证据无关的片段");
        }
        if (metadataHit != null && metadataHit < 1) {
            retrievalNotes.add("命中结果不满足样本里的过滤条件");
        }
        if (!generationRan) {
            generationNotes.add("这次没有生成答案");
        } else {
            if (faithfulness != null && faithfulness < 1) {
                generationNotes.add("答案里有上下文支撑不了的数字或编码");
            } else if (faithfulness == null) {
                generationNotes.add("答案没有可自动核对的数字或编码，需要人工看是否被上下文支撑");
            }
            if (answerRelevancy != null && answerRelevancy < relevancyBar(item)) {
                generationNotes.add("答案没有覆盖标准答案里的要点");
            }
            if (citationAccuracy != null && citationAccuracy < 1) {
                generationNotes.add("引用位置不能支撑对应结论");
            }
        }

        String retrievalStage = refuse && gold.isEmpty()
                ? (contextPrecision != null && contextPrecision < 1 ? "有问题" : "不适用")
                : (retrievalNotes.isEmpty() ? "通过" : "有问题");
        String generationStage = !generationRan
                ? "未评估"
                : (generationNotes.stream().anyMatch(note -> !note.startsWith("答案没有可自动核对")) ? "有问题"
                : (generationNotes.isEmpty() ? "通过" : "需人工"));

        return new CaseScore(
                item.id(),
                item.category(),
                item.answerType(),
                item.question(),
                hitRate,
                mrr,
                contextRecall,
                contextPrecision,
                metadataHit,
                faithfulness,
                answerRelevancy,
                citationAccuracy,
                retrievalMs,
                rerankMs,
                generationMs,
                contextTexts.stream().mapToInt(String::length).sum(),
                retrievalStage,
                generationStage,
                List.copyOf(retrievalNotes),
                List.copyOf(generationNotes));
    }

    public static EvalReport aggregate(List<CaseScore> scores) {
        Map<String, Double> retrieval = new LinkedHashMap<>();
        retrieval.put("hitRate", mean(scores, CaseScore::hitRate));
        retrieval.put("mrr", mean(scores, CaseScore::mrr));
        retrieval.put("contextRecall", mean(scores, CaseScore::contextRecall));
        retrieval.put("contextPrecision", mean(scores, CaseScore::contextPrecision));
        retrieval.put("metadataHit", mean(scores, CaseScore::metadataHit));

        Map<String, Double> generation = new LinkedHashMap<>();
        generation.put("faithfulness", mean(scores, CaseScore::faithfulness));
        generation.put("answerRelevancy", mean(scores, CaseScore::answerRelevancy));
        generation.put("citationAccuracy", mean(scores, CaseScore::citationAccuracy));

        Map<String, Object> engineering = new LinkedHashMap<>();
        engineering.put("p95RetrievalMs", percentile(scores.stream().map(CaseScore::retrievalMs).toList()));
        engineering.put("p95RerankMs", percentile(scores.stream().map(CaseScore::rerankMs).toList()));
        engineering.put("p95GenerationMs", percentile(scores.stream().map(CaseScore::generationMs).toList()));
        engineering.put("estimatedContextTokens", scores.stream().mapToInt(CaseScore::contextChars).sum());
        engineering.put("cacheHitRate", null);

        List<String> review = new ArrayList<>();
        Set<String> seenCategory = new LinkedHashSet<>();
        for (CaseScore score : scores) {
            boolean calibration = seenCategory.add(score.category());
            boolean flagged = !"通过".equals(score.retrievalStage()) && !"不适用".equals(score.retrievalStage())
                    || "有问题".equals(score.generationStage())
                    || "需人工".equals(score.generationStage());
            if ((calibration || flagged) && !review.contains(score.id())) {
                review.add(score.id());
            }
        }
        return new EvalReport(scores.size(), retrieval, generation, engineering, scores, review, JUDGE_NOTE, null);
    }

    static boolean hit(List<String> ranked, List<String> gold, int k) {
        int end = Math.min(k, ranked.size());
        for (int i = 0; i < end; i++) {
            if (coversAny(ranked.get(i), gold)) {
                return true;
            }
        }
        return false;
    }

    static double reciprocalRank(List<String> ranked, List<String> gold, int k) {
        int end = Math.min(k, ranked.size());
        for (int i = 0; i < end; i++) {
            if (coversAny(ranked.get(i), gold)) {
                return 1d / (i + 1);
            }
        }
        return 0;
    }

    static double recall(List<String> context, List<String> gold) {
        int found = 0;
        for (String snippet : gold) {
            if (context.stream().anyMatch(chunk -> covers(chunk, snippet))) {
                found++;
            }
        }
        return gold.isEmpty() ? 1 : (double) found / gold.size();
    }

    static double precision(List<String> context, List<String> gold) {
        if (context.isEmpty()) {
            return gold.isEmpty() ? 1 : 0;
        }
        int relevant = 0;
        for (String chunk : context) {
            if (coversAny(chunk, gold)) {
                relevant++;
            }
        }
        return (double) relevant / context.size();
    }

    static Double metadataHit(List<String> ranked, Map<String, String> filter, int k) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        int end = Math.min(k, ranked.size());
        for (int i = 0; i < end; i++) {
            if (satisfies(ranked.get(i), filter)) {
                return 1d;
            }
        }
        return 0d;
    }

    static Double faithfulness(String answer, List<String> context, boolean refuse) {
        String joined = String.join("\n", context);
        List<String> atoms = atoms(answer);
        if (refuse) {
            boolean refused = isRefusal(answer);
            boolean leaked = atoms.stream().anyMatch(atom -> !joined.contains(atom));
            if (!refused || leaked) {
                return 0d;
            }
            return 1d;
        }
        if (atoms.isEmpty()) {
            return null;
        }
        long supported = atoms.stream().filter(joined::contains).count();
        return (double) supported / atoms.size();
    }

    private static double relevancyBar(EvalCase item) {
        if ("refuse".equals(item.answerType()) || !atoms(item.goldenAnswer()).isEmpty()) {
            return 1;
        }
        return 0.45;
    }

    static double relevancy(String answer, String goldenAnswer, boolean refuse) {
        if (refuse) {
            return isRefusal(answer) ? 1d : 0d;
        }
        List<String> expected = atoms(goldenAnswer);
        if (!expected.isEmpty()) {
            long hit = expected.stream().filter(answer::contains).count();
            return (double) hit / expected.size();
        }
        Set<String> gold = bigrams(normalize(goldenAnswer));
        if (gold.isEmpty()) {
            return 0;
        }
        Set<String> actual = bigrams(normalize(answer));
        long shared = gold.stream().filter(actual::contains).count();
        return (double) shared / gold.size();
    }

    static Double citations(String answer, List<String> context, boolean refuse) {
        Matcher matcher = CITATION.matcher(answer);
        List<int[]> spans = new ArrayList<>();
        while (matcher.find()) {
            spans.add(new int[] {matcher.start(), matcher.end(), Integer.parseInt(matcher.group(1))});
        }
        if (spans.isEmpty()) {
            return refuse ? 1d : 0d;
        }
        int correct = 0;
        int previous = 0;
        for (int[] span : spans) {
            String clause = answer.substring(previous, span[0]);
            previous = span[1];
            int index = span[2] - 1;
            if (index < 0 || index >= context.size()) {
                continue;
            }
            if (clauseSupported(clause, context.get(index))) {
                correct++;
            }
        }
        return spans.isEmpty() ? null : (double) correct / spans.size();
    }

    private static boolean clauseSupported(String clause, String chunk) {
        List<String> atoms = atoms(clause);
        if (!atoms.isEmpty()) {
            return atoms.stream().allMatch(chunk::contains);
        }
        String compact = normalize(clause);
        return compact.length() >= 4 && normalize(chunk).contains(compact);
    }

    private static boolean coversAny(String chunk, List<String> gold) {
        for (String snippet : gold) {
            if (covers(chunk, snippet)) {
                return true;
            }
        }
        return false;
    }

    private static boolean covers(String chunk, String snippet) {
        if (chunk == null || snippet == null || snippet.isBlank()) {
            return false;
        }
        return normalize(chunk).contains(normalize(snippet));
    }

    private static boolean satisfies(String chunk, Map<String, String> filter) {
        String text = chunk == null ? "" : chunk;
        for (String value : filter.values()) {
            if (value != null && !value.isBlank() && !text.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRefusal(String answer) {
        for (String phrase : REFUSAL) {
            if (answer.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    static List<String> atoms(String text) {
        if (text == null) {
            return List.of();
        }
        Matcher matcher = ATOM.matcher(text.replaceAll("\\[\\d+]", ""));
        List<String> atoms = new ArrayList<>();
        while (matcher.find()) {
            atoms.add(matcher.group());
        }
        return atoms;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static Set<String> bigrams(String text) {
        Set<String> grams = new LinkedHashSet<>();
        if (text.length() < 2) {
            return grams;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            grams.add(text.substring(i, i + 2));
        }
        return grams;
    }

    private static Double mean(List<CaseScore> scores, java.util.function.Function<CaseScore, Double> getter) {
        double sum = 0;
        int count = 0;
        for (CaseScore score : scores) {
            Double value = getter.apply(score);
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    static Long percentile(List<Long> values) {
        List<Long> present = new ArrayList<>();
        for (Long value : values) {
            if (value != null) {
                present.add(value);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        present.sort(Long::compareTo);
        int index = (int) Math.ceil(present.size() * 0.95) - 1;
        return present.get(Math.max(0, index));
    }

    public static List<String> contents(List<RankedChunk> chunks) {
        if (chunks == null) {
            return List.of();
        }
        return chunks.stream().map(chunk -> chunk.content() == null ? "" : chunk.content()).toList();
    }

    public static List<String> metadataText(List<RankedChunk> chunks) {
        if (chunks == null) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> (chunk.fileName() == null ? "" : chunk.fileName()) + "\n"
                        + (chunk.heading() == null ? "" : chunk.heading()) + "\n"
                        + (chunk.content() == null ? "" : chunk.content()))
                .toList();
    }
}
