package com.study.rag.retrieve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceOrganizerTest {

    @Test
    void putsMostRelevantDocumentFirstAndKeepsOriginalChunkOrder() {
        List<RankedChunk> ordered = EvidenceOrganizer.organize("住宿标准是多少", List.of(
                chunk("later", "a", "手册.md", "住宿", "后段每晚 700 元", 2, 0.95),
                chunk("earlier", "a", "手册.md", "住宿", "前段每晚 700 元", 0, 0.80),
                chunk("other", "b", "制度.md", "差旅", "差旅需要审批", 0, 0.99)));

        assertEquals(List.of("other", "earlier", "later"), ordered.stream().map(RankedChunk::chunkId).toList());
        assertTrue(ordered.get(0).section().startsWith("高置信度"));
    }

    @Test
    void keepsLowConfidenceAfterHighConfidence() {
        List<RankedChunk> ordered = EvidenceOrganizer.organize("住宿标准", List.of(
                chunk("high-a", "a", "手册.md", "住宿", "每晚 700 元", 0, 0.95),
                chunk("low-a", "a", "手册.md", "住宿", "补充说明", 1, 0.30),
                chunk("high-b", "b", "制度.md", "差旅", "需要审批", 0, 0.90)));

        assertEquals(List.of("high-a", "high-b", "low-a"), ordered.stream().map(RankedChunk::chunkId).toList());
        assertTrue(ordered.get(2).section().startsWith("低置信度"));
    }

    @Test
    void groupsComparisonByTopic() {
        List<RankedChunk> ordered = EvidenceOrganizer.organize("对比住宿和差旅", List.of(
                chunk("stay-later", "b", "手册.md", "住宿", "住宿后段", 1, 0.90),
                chunk("travel", "c", "制度.md", "差旅", "差旅标准", 0, 0.95),
                chunk("stay-earlier", "b", "手册.md", "住宿", "住宿前段", 0, 0.80)));

        assertEquals(List.of("travel", "stay-earlier", "stay-later"), ordered.stream().map(RankedChunk::chunkId).toList());
        assertEquals("主题：差旅", ordered.get(0).section());
        assertEquals("主题：住宿", ordered.get(1).section());
    }

    @Test
    void ordersTimelineByTimeInTheText() {
        List<RankedChunk> ordered = EvidenceOrganizer.organize("制度如何演变", List.of(
                chunk("y2024", "a", "2024版手册.md", "住宿", "2024年3月标准为 800 元", 0, 0.80),
                chunk("y2022", "b", "2022版手册.md", "住宿", "2022年标准为 600 元", 0, 0.90)));

        assertEquals(List.of("y2022", "y2024"), ordered.stream().map(RankedChunk::chunkId).toList());
        assertEquals("时间线", ordered.get(0).section());
    }

    @Test
    void ordersTroubleshootingBySymptomCauseStepsAndCaution() {
        List<RankedChunk> ordered = EvidenceOrganizer.organize("服务报错怎么办", List.of(
                chunk("step", "a", "手册.md", "排障", "处理步骤：重启服务", 0, 0.95),
                chunk("symptom", "a", "手册.md", "排障", "现象：连接失败", 1, 0.90),
                chunk("cause", "a", "手册.md", "排障", "原因：因为超时", 2, 0.85),
                chunk("caution", "a", "手册.md", "排障", "注意：不要删除数据", 3, 0.80)));

        assertEquals(List.of("symptom", "cause", "step", "caution"), ordered.stream().map(RankedChunk::chunkId).toList());
        assertEquals(List.of("现象", "原因", "处理步骤", "注意事项"), ordered.stream().map(RankedChunk::section).toList());
    }

    @Test
    void annotatesContradictionsWithVersionAndTime() {
        RankedChunk oldRule = chunk("old", "a", "2023版手册.md", "住宿", "住宿标准为每晚 600 元", 0, 0.90);
        RankedChunk newRule = chunk("new", "b", "2024版手册.md", "住宿", "住宿标准为每晚 800 元", 0, 0.88);
        oldRule = oldRule.withMeta(1_700_000_000_000L, null);
        newRule = newRule.withMeta(1_720_000_000_000L, null);

        List<RankedChunk> ordered = EvidenceOrganizer.organize("住宿标准", List.of(oldRule, newRule));

        assertTrue(ordered.get(0).conflictNote().contains("可能冲突"));
        assertTrue(ordered.get(0).conflictNote().contains("2023版"));
        assertTrue(ordered.get(0).conflictNote().contains("2024版"));
        assertTrue(ordered.get(1).conflictNote().contains("入库时间"));
        assertEquals("2023版", ordered.stream().filter(chunk -> chunk.chunkId().equals("old")).findFirst().orElseThrow().version());
    }

    private static RankedChunk chunk(String id, String docId, String fileName, String heading, String content, int index, double score) {
        return new RankedChunk(id, docId, fileName, "md", null, null, heading, content, index, score, 0L, null, null, null);
    }
}
