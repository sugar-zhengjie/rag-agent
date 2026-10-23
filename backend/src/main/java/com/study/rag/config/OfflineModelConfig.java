package com.study.rag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.ai.dashscope", name = "enabled", havingValue = "false")
public class OfflineModelConfig {

    static final String MISSING_KEY = "未配置 DASHSCOPE_API_KEY";

    @Bean
    EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new IllegalStateException(MISSING_KEY);
            }

            @Override
            public float[] embed(Document document) {
                throw new IllegalStateException(MISSING_KEY);
            }
        };
    }

    @Bean
    ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException(MISSING_KEY);
            }
        };
    }
}
