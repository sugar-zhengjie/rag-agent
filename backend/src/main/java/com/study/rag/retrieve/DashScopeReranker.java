package com.study.rag.retrieve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.rag.config.RagProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class DashScopeReranker implements Reranker {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final String apiKey;
    private final String rerankModel;

    public DashScopeReranker(RestClient.Builder restClientBuilder,
                             ObjectMapper objectMapper,
                             RagProperties properties,
                             @Value("${spring.ai.dashscope.api-key:}") String apiKey,
                             @Value("${spring.ai.dashscope.rerank.options.model:qwen3.7-text-rerank}") String rerankModel) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKey = apiKey;
        this.rerankModel = rerankModel;
    }

    @Override
    public List<RankedChunk> rerank(String query, List<RankedChunk> candidates, int topN) {
        if (!"dashscope".equalsIgnoreCase(properties.getRerank().getProvider())) {
            return limit(candidates, topN);
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY");
        }
        List<String> documents = candidates.stream().map(RankedChunk::content).toList();
        Map<String, Object> body = Map.of(
                "model", rerankModel,
                "input", Map.of("query", query, "documents", documents),
                "parameters", Map.of("return_documents", false, "top_n", Math.min(topN, documents.size())));
        String url = trimSlash(properties.getRerank().getBaseUrl()) + properties.getRerank().getPath();
        String raw;
        try {
            raw = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new IllegalStateException("精排请求失败: " + ex.getMessage(), ex);
        }
        return parse(raw, candidates, topN);
    }

    private List<RankedChunk> parse(String raw, List<RankedChunk> candidates, int topN) {
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            if (root.hasNonNull("code") && !root.path("output").has("results")) {
                String message = root.path("message").asText(root.path("code").asText("精排失败"));
                throw new IllegalStateException("精排失败: " + message);
            }
            JsonNode results = root.path("output").path("results");
            if (!results.isArray()) {
                throw new IllegalStateException("精排返回缺少 results");
            }
            List<RankedChunk> ranked = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                double score = item.path("relevance_score").asDouble(item.path("score").asDouble(0d));
                RankedChunk source = candidates.get(index);
                ranked.add(copy(source, score));
            }
            ranked.sort(Comparator.comparingDouble(RankedChunk::score).reversed());
            return limit(ranked, topN);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("精排结果解析失败: " + ex.getMessage(), ex);
        }
    }

    static RankedChunk copy(RankedChunk source, double score) {
        return source.withScore(score);
    }

    static List<RankedChunk> limit(List<RankedChunk> chunks, int topN) {
        if (chunks.size() <= topN) {
            return chunks;
        }
        return List.copyOf(chunks.subList(0, topN));
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
