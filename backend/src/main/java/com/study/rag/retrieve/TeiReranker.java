package com.study.rag.retrieve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.rag.config.RagProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class TeiReranker {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public TeiReranker(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, RagProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<RankedChunk> rerank(String query, List<RankedChunk> candidates, int topN) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> texts = candidates.stream().map(RankedChunk::content).toList();
        String raw;
        try {
            raw = restClient.post()
                    .uri(properties.getRerank().getTeiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "texts", texts))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new IllegalStateException("BGE 精排请求失败: " + ex.getMessage(), ex);
        }
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "[]" : raw);
            if (!root.isArray()) {
                throw new IllegalStateException("BGE 精排返回不是数组");
            }
            List<RankedChunk> ranked = new ArrayList<>();
            for (JsonNode item : root) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                ranked.add(DashScopeReranker.copy(candidates.get(index), item.path("score").asDouble(0d)));
            }
            ranked.sort(Comparator.comparingDouble(RankedChunk::score).reversed());
            return DashScopeReranker.limit(ranked, topN);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("BGE 精排结果解析失败: " + ex.getMessage(), ex);
        }
    }
}
