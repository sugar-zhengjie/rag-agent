package com.study.rag.embed;

import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import com.study.rag.config.RagProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingClient {

    public static final String DOCUMENT = "document";
    public static final String QUERY = "query";

    private final EmbeddingModel embeddingModel;
    private final RagProperties properties;
    private final String apiKey;
    private final String embeddingModelName;

    public EmbeddingClient(EmbeddingModel embeddingModel,
                           RagProperties properties,
                           @Value("${spring.ai.dashscope.api-key:}") String apiKey,
                           @Value("${spring.ai.dashscope.embedding.options.model:qwen3.7-text-embedding-flash}") String embeddingModelName) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.apiKey = apiKey;
        this.embeddingModelName = embeddingModelName;
    }

    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, DOCUMENT);
    }

    public float[] embedQuery(String text) {
        return embed(List.of(text), QUERY).get(0);
    }

    private List<float[]> embed(List<String> texts, String textType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY");
        }
        int batchSize = Math.max(1, properties.getEmbedding().getBatchSize());
        int dimensions = properties.getEmbedding().getDimensions();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + batchSize));
            DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                    .model(embeddingModelName)
                    .textType(textType)
                    .dimensions(dimensions)
                    .build();
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, options));
            if (response.getResults().size() != batch.size()) {
                throw new IllegalStateException("Embedding 返回数量与输入不一致");
            }
            for (var result : response.getResults()) {
                float[] vector = result.getOutput();
                if (vector.length != dimensions) {
                    throw new IllegalStateException("向量维度是 " + vector.length + "，索引需要 " + dimensions);
                }
                vectors.add(vector);
            }
        }
        return vectors;
    }
}
