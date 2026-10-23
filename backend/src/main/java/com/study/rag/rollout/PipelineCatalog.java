package com.study.rag.rollout;

import com.study.rag.chat.RagChatService;
import com.study.rag.config.RagProperties;
import com.study.rag.ingest.TextCleaner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PipelineCatalog {

    private final RagProperties properties;
    private final String embeddingModel;
    private final String rerankModel;

    public PipelineCatalog(RagProperties properties,
                           @Value("${spring.ai.dashscope.embedding.options.model:qwen3.7-text-embedding-flash}") String embeddingModel,
                           @Value("${spring.ai.dashscope.rerank.options.model:qwen3.7-text-rerank}") String rerankModel) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
        this.rerankModel = rerankModel;
    }

    public PipelineSnapshot current() {
        return new PipelineSnapshot(
                TextCleaner.VERSION,
                properties.getChunk().getStrategy(),
                embeddingModel + ":" + properties.getEmbedding().getDimensions(),
                properties.getIndex().getInvertedIndex() + "+dense-v1",
                properties.getRewrite().getMode(),
                properties.getRerank().getProvider() + ":" + rerankModel,
                RagChatService.PROMPT_VERSION);
    }
}
