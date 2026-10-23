package com.study.rag.retrieve;

import java.util.List;

public record RetrievalResult(
        String originalQuery,
        String rewrittenQuery,
        List<RankedChunk> bm25,
        List<RankedChunk> knn,
        List<RankedChunk> rrf,
        List<RankedChunk> reranked,
        List<RankedChunk> compressed,
        String promptLayout,
        String warning,
        StageTiming timing) {
}
