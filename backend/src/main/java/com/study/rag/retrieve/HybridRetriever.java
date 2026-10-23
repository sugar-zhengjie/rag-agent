package com.study.rag.retrieve;

import com.study.rag.acl.DatasetCatalog;
import com.study.rag.chat.ConversationMemory;
import com.study.rag.chat.QueryRewriter;
import com.study.rag.config.RagProperties;
import com.study.rag.embed.EmbeddingClient;
import com.study.rag.index.ChunkHit;
import com.study.rag.index.ChunkIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final ChunkIndexService indexService;
    private final EmbeddingClient embeddingClient;
    private final Reranker reranker;
    private final ContextCompressor contextCompressor;
    private final QueryRewriter queryRewriter;
    private final ConversationMemory memory;
    private final RagProperties properties;
    private final DatasetCatalog datasetCatalog;

    public HybridRetriever(ChunkIndexService indexService,
                           EmbeddingClient embeddingClient,
                           Reranker reranker,
                           ContextCompressor contextCompressor,
                           QueryRewriter queryRewriter,
                           ConversationMemory memory,
                           RagProperties properties,
                           DatasetCatalog datasetCatalog) {
        this.indexService = indexService;
        this.embeddingClient = embeddingClient;
        this.reranker = reranker;
        this.contextCompressor = contextCompressor;
        this.queryRewriter = queryRewriter;
        this.memory = memory;
        this.properties = properties;
        this.datasetCatalog = datasetCatalog;
    }

    public RetrievalResult retrieve(String question, String sessionId, Integer topK, String userId) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        indexService.ensureIndexes();
        long started = System.nanoTime();
        String original = question.trim();
        String warning = null;
        List<String> queries;
        try {
            queries = queryRewriter.queries(original, memory.history(userId, sessionId));
        } catch (RuntimeException ex) {
            warning = "查询改写失败，已使用原问题: " + ex.getMessage();
            queries = List.of(original);
        }
        if (queries.isEmpty()) {
            queries = List.of(original);
        }
        String rewritten = String.join("\n", queries);
        List<String> datasetIds = datasetCatalog.visibleIds(userId);
        if (datasetIds.isEmpty()) {
            StageTiming timing = new StageTiming(millis(started), 0, 0);
            return new RetrievalResult(original, rewritten, List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, warning, timing);
        }
        RagProperties.Retrieval retrieval = properties.getRetrieval();
        int limit = topK == null ? retrieval.getTopK() : Math.max(1, Math.min(topK, 20));
        List<List<RankedChunk>> bm25Lists = new ArrayList<>();
        List<List<RankedChunk>> knnLists = new ArrayList<>();
        for (String query : queries) {
            float[] vector = embeddingClient.embedQuery(query);
            List<Float> queryVector = new ArrayList<>(vector.length);
            for (float value : vector) {
                queryVector.add(value);
            }
            bm25Lists.add(mapHits(indexService.searchBm25(query, retrieval.getBm25Size(), datasetIds)));
            knnLists.add(mapHits(indexService.searchKnn(queryVector, retrieval.getKnnK(), retrieval.getKnnNumCandidates(), datasetIds)));
        }
        List<RankedChunk> bm25 = queries.size() == 1 ? bm25Lists.get(0) : fuse(bm25Lists, retrieval.getRrfRankConstant());
        List<RankedChunk> knn = queries.size() == 1 ? knnLists.get(0) : fuse(knnLists, retrieval.getRrfRankConstant());
        List<RankedChunk> fused = ReciprocalRankFusion
                .fuse(List.of(bm25, knn), RankedChunk::chunkId, retrieval.getRrfRankConstant())
                .stream()
                .map(item -> DashScopeReranker.copy(item.item(), item.score()))
                .toList();
        List<RankedChunk> rerankInput = DashScopeReranker.limit(fused, retrieval.getRerankInputSize());
        List<RankedChunk> reranked;
        long rerankStarted = System.nanoTime();
        try {
            reranked = reranker.rerank(rewritten, rerankInput, limit);
        } catch (RuntimeException ex) {
            log.error("精排模型调用失败", ex);
            String rerankWarning = "精排失败，已使用 RRF 顺序: " + ex.getMessage();
            warning = warning == null ? rerankWarning : warning + "；" + rerankWarning;
            reranked = DashScopeReranker.limit(fused, limit);
        }
        reranked = ParentExpander.expand(reranked);
        ContextCompressor.CompressionOutcome compressed = contextCompressor.compress(rewritten, reranked);
        if (compressed.warning() != null) {
            warning = warning == null ? compressed.warning() : warning + "；" + compressed.warning();
        }
        int contextChars = compressed.chunks().stream()
                .mapToInt(chunk -> chunk.content() == null ? 0 : chunk.content().length())
                .sum();
        StageTiming timing = new StageTiming(millis(started), millis(rerankStarted), contextChars);
        return new RetrievalResult(original, rewritten, bm25, knn, fused, reranked, compressed.chunks(),
                compressed.layout(), warning, timing);
    }

    private static List<RankedChunk> fuse(List<List<RankedChunk>> lists, int rankConstant) {
        return ReciprocalRankFusion.fuse(lists, RankedChunk::chunkId, rankConstant)
                .stream()
                .map(item -> DashScopeReranker.copy(item.item(), item.score()))
                .toList();
    }

    private static long millis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private static List<RankedChunk> mapHits(List<ChunkHit> hits) {
        List<RankedChunk> ranked = new ArrayList<>(hits.size());
        for (ChunkHit hit : hits) {
            var doc = hit.document();
            ranked.add(new RankedChunk(
                    doc.getChunkId(),
                    doc.getDocId(),
                    doc.getFileName(),
                    doc.getFileType(),
                    doc.getPage(),
                    doc.getSheet(),
                    doc.getHeading(),
                    doc.getContent(),
                    doc.getChunkIndex(),
                    hit.score(),
                    doc.getCreatedAt(),
                    null,
                    null,
                    null,
                    doc.getParentContent()));
        }
        return ranked;
    }
}
