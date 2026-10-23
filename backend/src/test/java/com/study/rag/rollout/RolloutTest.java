package com.study.rag.rollout;

import com.study.rag.chat.RuleQuery;
import com.study.rag.ingest.ChunkStrategies;
import com.study.rag.ingest.TextCleaner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolloutTest {

    @Test
    void dropsPageNoiseAndKeepsTheBody() {
        String cleaned = TextCleaner.clean("员工出差前需要提交申请。\n- 1 -\n\n\n第 2 页\n报销时提供发票、行程单和审批记录。");

        assertTrue(cleaned.contains("员工出差前需要提交申请"));
        assertTrue(cleaned.contains("报销时提供发票、行程单和审批记录"));
        assertFalse(cleaned.contains("第 2 页"));
        assertFalse(cleaned.contains("- 1 -"));
    }

    @Test
    void comparesChunkStrategiesAndKeepsParentText() {
        String text = "报销需要发票。报销需要审批。周末食堂关闭。";
        List<ChunkStrategies.StrategyHit> hits = ChunkStrategies.compare(text, List.of("报销需要发票"));
        assertEquals(4, hits.size());
        assertTrue(hits.stream().allMatch(hit -> hit.evidenceRecall() == 1d));

        var parent = ChunkStrategies.retarget("parent-child",
                List.of(new com.study.rag.ingest.ChunkDraft(text, 3, null, "差旅", 0)),
                new com.study.rag.ingest.RecursiveTextSplitter(120, 20, 10));
        assertFalse(parent.isEmpty());
        assertEquals(text, parent.get(0).parentContent());
        assertEquals(3, parent.get(0).page());
        assertEquals("差旅", parent.get(0).heading());
    }

    @Test
    void rewritesCodesAndSplitsExtraIntentsWithoutSplittingAHop() {
        assertTrue(RuleQuery.normalize("ERR1001 是啥").contains("ERR-1001"));
        assertTrue(RuleQuery.normalize("住宿多少钱").contains("标准是多少"));
        assertEquals(2, RuleQuery.splitIntents("差旅报销需要什么，另外错误码是什么").size());
        assertEquals(1, RuleQuery.splitIntents("申请通过后再订协议酒店").size());
    }

    @Test
    void rejectsARoundThatChangesTooManyStages() {
        PipelineSnapshot baseline = new PipelineSnapshot("clean-v1", "structured", "text-embedding-v3:1024",
                "bm25+dense-v1", "baseline", "dashscope:qwen3-rerank", "cite-refuse-v1");
        PipelineSnapshot oneChange = baseline.with(null, "parent-child", null, null, null, null, null);
        PipelineSnapshot threeChanges = baseline.with(null, "semantic", null, null, "expanded", "none:none", null);

        assertEquals(List.of("chunk"), ExperimentLedger.changed(baseline, oneChange));
        assertEquals(3, ExperimentLedger.changed(baseline, threeChanges).size());
        ExperimentLedger.requireSmallChange(baseline, oneChange);
        assertThrows(IllegalArgumentException.class, () -> ExperimentLedger.requireSmallChange(baseline, threeChanges));
    }
}
