package com.study.rag.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalChunkStoreTest {

    @Test
    void searchesTheVisibleDatasetAndDropsDeletedChunks() {
        LocalChunkStore store = new LocalChunkStore();
        store.index(document("d1", "team"), List.of(chunk("c1", "d1", "team", "一线城市住宿标准是每晚 600 元", 1f, 0f)));
        store.index(document("d2", "secret"), List.of(chunk("c2", "d2", "secret", "一线城市住宿标准是每晚 900 元", 0f, 1f)));

        List<ChunkHit> keyword = store.searchBm25("住宿标准", 5, List.of("team"));
        assertEquals(1, keyword.size());
        assertEquals("c1", keyword.get(0).document().getChunkId());

        List<ChunkHit> vector = store.searchKnn(List.of(1f, 0f), 5, List.of("team"));
        assertEquals("c1", vector.get(0).document().getChunkId());

        assertTrue(store.deleteDocument("d1"));
        assertTrue(store.searchBm25("住宿标准", 5, List.of("team")).isEmpty());
        assertTrue(store.listChunks("d1").isEmpty());
    }

    @Test
    void keepsExactCodesAsTokens() {
        LocalChunkStore store = new LocalChunkStore();
        store.index(document("d1", "team"), List.of(
                chunk("code", "d1", "team", "审批失败时返回 ERR-1001", 1f, 0f),
                chunk("other", "d1", "team", "住宿需要发票", 0f, 1f)));

        List<ChunkHit> hits = store.searchBm25("ERR-1001", 5, List.of("team"));
        assertEquals("code", hits.get(0).document().getChunkId());
    }

    private static StoredDocument document(String id, String datasetId) {
        StoredDocument document = new StoredDocument();
        document.setDocId(id);
        document.setDatasetId(datasetId);
        document.setFileName(id + ".md");
        document.setCreatedAt(1L);
        return document;
    }

    private static ChunkDocument chunk(String id, String docId, String datasetId, String content, float x, float y) {
        ChunkDocument chunk = new ChunkDocument();
        chunk.setChunkId(id);
        chunk.setDocId(docId);
        chunk.setDatasetId(datasetId);
        chunk.setContent(content);
        chunk.setEmbedding(List.of(x, y));
        return chunk;
    }
}
