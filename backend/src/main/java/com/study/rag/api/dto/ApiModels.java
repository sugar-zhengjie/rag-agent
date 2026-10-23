package com.study.rag.api.dto;

import com.study.rag.index.ChunkDocument;
import com.study.rag.index.StoredDocument;
import com.study.rag.retrieve.RankedChunk;
import com.study.rag.retrieve.RetrievalResult;

import java.time.Instant;
import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public record DocumentView(String docId, String fileName, String fileType, int chunkCount, String createdAt, String datasetId) {

        public static DocumentView from(StoredDocument document) {
            return new DocumentView(
                    document.getDocId(),
                    document.getFileName(),
                    document.getFileType(),
                    document.getChunkCount(),
                    Instant.ofEpochMilli(document.getCreatedAt()).toString(),
                    document.getDatasetId());
        }
    }

    public record ChunkView(String chunkId, int chunkIndex, Integer page, String sheet, String heading, String content) {

        public static ChunkView from(ChunkDocument document) {
            return new ChunkView(
                    document.getChunkId(),
                    document.getChunkIndex(),
                    document.getPage(),
                    document.getSheet(),
                    document.getHeading(),
                    document.getContent());
        }
    }

    public record HitView(
            String chunkId,
            String docId,
            String fileName,
            String fileType,
            Integer page,
            String sheet,
            String heading,
            String content,
            int chunkIndex,
            double score,
            long createdAt,
            String version,
            String section,
            String conflictNote) {

        public static HitView from(RankedChunk chunk) {
            return new HitView(
                    chunk.chunkId(),
                    chunk.docId(),
                    chunk.fileName(),
                    chunk.fileType(),
                    chunk.page(),
                    chunk.sheet(),
                    chunk.heading(),
                    chunk.content(),
                    chunk.chunkIndex(),
                    chunk.score(),
                    chunk.createdAt(),
                    chunk.version(),
                    chunk.section(),
                    chunk.conflictNote());
        }

        public static List<HitView> from(List<RankedChunk> chunks) {
            return chunks.stream().map(HitView::from).toList();
        }
    }

    public record RetrievalView(
            String originalQuery,
            String rewrittenQuery,
            List<HitView> bm25,
            List<HitView> knn,
            List<HitView> rrf,
            List<HitView> reranked,
            List<HitView> compressed,
            String promptLayout,
            String warning) {

        public static RetrievalView from(RetrievalResult result) {
            return new RetrievalView(
                    result.originalQuery(),
                    result.rewrittenQuery(),
                    HitView.from(result.bm25()),
                    HitView.from(result.knn()),
                    HitView.from(result.rrf()),
                    HitView.from(result.reranked()),
                    HitView.from(result.compressed()),
                    result.promptLayout(),
                    result.warning());
        }
    }

    public record AskRequest(String question, String sessionId, Integer topK) {
    }

    public record ChatResponse(String answer, String rewrittenQuery, List<HitView> citations, RetrievalView trace) {
    }

    public record HealthView(
            boolean elasticsearch,
            boolean indexReady,
            boolean apiKeyConfigured,
            String embeddingModel,
            int dimensions,
            String chatModel,
            String rerankProvider,
            String rerankModel,
            String indexBackend) {
    }
}
