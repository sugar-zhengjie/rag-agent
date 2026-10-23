package com.study.rag.retrieve;

import com.study.rag.config.RagProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class RoutingReranker implements Reranker {

    private final RagProperties properties;
    private final DashScopeReranker dashScopeReranker;
    private final TeiReranker teiReranker;

    public RoutingReranker(RagProperties properties, DashScopeReranker dashScopeReranker, TeiReranker teiReranker) {
        this.properties = properties;
        this.dashScopeReranker = dashScopeReranker;
        this.teiReranker = teiReranker;
    }

    @Override
    public List<RankedChunk> rerank(String query, List<RankedChunk> candidates, int topN) {
        String provider = properties.getRerank().getProvider() == null
                ? "dashscope"
                : properties.getRerank().getProvider().trim().toLowerCase();
        return switch (provider) {
            case "none" -> DashScopeReranker.limit(candidates, topN);
            case "tei", "bge" -> teiReranker.rerank(query, candidates, topN);
            default -> dashScopeReranker.rerank(query, candidates, topN);
        };
    }
}
