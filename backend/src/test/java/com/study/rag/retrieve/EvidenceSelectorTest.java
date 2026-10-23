package com.study.rag.retrieve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceSelectorTest {

    @Test
    void keepsRelevantSentenceAndItsPreviousCondition() {
        String evidence = EvidenceSelector.select(
                "市内交通另计。差旅报销需要发票和审批记录。周末食堂不开放。",
                "差旅报销需要什么材料",
                1);

        assertTrue(evidence.contains("市内交通另计"));
        assertTrue(evidence.contains("差旅报销需要发票和审批记录"));
        assertFalse(evidence.contains("周末食堂不开放"));
    }

    @Test
    void dropsChunkWithNoQueryEvidence() {
        assertEquals("", EvidenceSelector.select("今天晴天。适合出游。", "差旅报销", 1));
    }

    @Test
    void dropsDuplicateOrContainedChunk() {
        RankedChunk longer = chunk("a", "差旅报销需要发票和审批记录", 0.9);
        RankedChunk contained = chunk("b", "需要发票", 0.4);
        RankedChunk other = chunk("c", "一线城市住宿标准为每晚六百元", 0.8);

        List<RankedChunk> kept = EvidenceSelector.dedup(List.of(contained, other, longer), 0.82);

        assertEquals(List.of("a", "c"), kept.stream().map(RankedChunk::chunkId).toList());
    }

    private static RankedChunk chunk(String id, String content, double score) {
        return new RankedChunk(id, "doc", "手册.md", "md", null, null, null, content, 0, score, 0L, null, null, null);
    }
}
