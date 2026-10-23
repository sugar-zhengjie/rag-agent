package com.study.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagScorerTest {

    private final EvalCase stay = new EvalCase(
            "F04",
            "frequent",
            "一线城市住宿多少钱？",
            "一线城市住宿标准为每晚 600 元。",
            List.of("一线城市住宿标准为每晚 600 元。"),
            Map.of("fileName", "eval-corpus.md", "heading", "住宿标准"),
            "fact");

    @Test
    void separatesMissedEvidenceFromAnAnswerTheContextCannotSupport() {
        CaseScore missed = RagScorer.score(stay, List.of("周末食堂不开放。"), List.of("周末食堂不开放。"), null, 5, null, null, null);
        assertEquals(0, missed.hitRate());
        assertEquals(0, missed.contextRecall());
        assertEquals("有问题", missed.retrievalStage());
        assertEquals("未评估", missed.generationStage());

        String evidence = "一线城市住宿标准为每晚 600 元。";
        CaseScore invented = RagScorer.score(stay,
                List.of(evidence),
                List.of(evidence),
                List.of("eval-corpus.md\n住宿标准\n" + evidence),
                "每晚 999 元[1]。",
                5, 12L, 4L, 30L);
        assertEquals(1, invented.hitRate());
        assertEquals(1, invented.mrr());
        assertEquals(1, invented.contextRecall());
        assertEquals(1, invented.metadataHit());
        assertEquals("通过", invented.retrievalStage());
        assertEquals(0, invented.faithfulness());
        assertEquals("有问题", invented.generationStage());
        assertTrue(invented.generationNotes().stream().anyMatch(note -> note.contains("支撑不了")));
    }

    @Test
    void scoresRankRecallPrecisionAndCitationSeparately() {
        EvalCase both = new EvalCase("X01", "failure", "住宿标准是多少？", "一线 600 元，二线 400 元。",
                List.of("一线城市住宿标准为每晚 600 元。", "二线城市住宿标准为每晚 400 元。"),
                Map.of(), "fact");
        CaseScore score = RagScorer.score(both,
                List.of("无关的食堂通知。", "一线城市住宿标准为每晚 600 元。"),
                List.of("一线城市住宿标准为每晚 600 元。", "周末食堂不开放。"),
                "一线是 600 元[2]。",
                5, null, null, null);

        assertEquals(1, score.hitRate());
        assertEquals(0.5, score.mrr());
        assertEquals(0.5, score.contextRecall());
        assertEquals(0.5, score.contextPrecision());
        assertEquals(0, score.citationAccuracy());
        assertEquals("有问题", score.retrievalStage());
    }

    @Test
    void acceptsRefusalWithoutContextAndRejectsALeakedCode() {
        EvalCase refuse = new EvalCase("R01", "refuse", "张三这个月工资多少？", "资料中没有相关内容。",
                List.of(), Map.of(), "refuse");
        CaseScore clean = RagScorer.score(refuse, List.of(), List.of(), "资料中没有相关内容。", 5, null, null, null);
        assertEquals("不适用", clean.retrievalStage());
        assertEquals(1, clean.faithfulness());
        assertEquals(1, clean.answerRelevancy());
        assertEquals(1, clean.citationAccuracy());
        assertEquals("通过", clean.generationStage());

        CaseScore leaked = RagScorer.score(refuse, List.of("无关"), List.of("无关"), "工资是 20000 元。", 5, null, null, null);
        assertEquals("有问题", leaked.retrievalStage());
        assertEquals(0, leaked.faithfulness());
        assertEquals(0, leaked.answerRelevancy());
    }

    @Test
    void datasetCoversTheMinimumSetAndGroundsEvidenceInTheCorpus() throws Exception {
        List<EvalCase> cases = new EvalDataset(new ObjectMapper()).read();
        assertTrue(cases.size() >= 50 && cases.size() <= 100);
        assertEquals(Set.of("frequent", "failure", "critical", "multihop", "exact", "stale", "refuse"),
                cases.stream().map(EvalCase::category).collect(Collectors.toSet()));
        assertEquals(Set.of("fact", "procedure", "compare", "refuse", "summary"),
                cases.stream().map(EvalCase::answerType).collect(Collectors.toSet()));

        String corpus = Files.readString(corpusPath());
        for (EvalCase item : cases) {
            assertTrue(item.question() != null && !item.question().isBlank());
            assertTrue(item.goldenAnswer() != null && !item.goldenAnswer().isBlank());
            assertTrue(item.metadataFilter() != null);
            if ("refuse".equals(item.answerType())) {
                assertTrue(item.goldenContext().isEmpty());
                continue;
            }
            assertTrue(!item.goldenContext().isEmpty(), item.id());
            for (String snippet : item.goldenContext()) {
                assertTrue(corpus.contains(snippet), item.id() + " 缺少证据: " + snippet);
            }
        }
    }

    @Test
    void aggregateKeepsRetrievalAndGenerationMeansApart() {
        CaseScore retrieval = RagScorer.score(stay, List.of("没有证据"), List.of("没有证据"), null, 5, 10L, 3L, null);
        EvalReport report = RagScorer.aggregate(List.of(retrieval));
        assertEquals(0, report.retrieval().get("hitRate"));
        assertNull(report.generation().get("faithfulness"));
        assertEquals(10L, report.engineering().get("p95RetrievalMs"));
        assertNull(report.engineering().get("cacheHitRate"));
        assertTrue(report.humanReview().contains("F04"));
        assertTrue(report.judgeNote().contains("不是事实裁判"));
    }

    private static Path corpusPath() {
        Path fromBackend = Path.of("..", "samples", "eval-corpus.md");
        if (Files.exists(fromBackend)) {
            return fromBackend;
        }
        return Path.of("samples", "eval-corpus.md");
    }
}
