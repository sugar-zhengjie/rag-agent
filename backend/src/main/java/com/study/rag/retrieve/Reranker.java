package com.study.rag.retrieve;

import java.util.List;

public interface Reranker {

    List<RankedChunk> rerank(String query, List<RankedChunk> candidates, int topN);
}
