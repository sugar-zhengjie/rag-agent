package com.study.rag.chat;

import com.study.rag.config.RagProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryRewriter {

    private final ChatClient chatClient;
    private final RagProperties properties;

    public QueryRewriter(ChatClient chatClient, RagProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    public String rewrite(String question, List<Turn> history) {
        List<String> queries = queries(question, history);
        return queries.isEmpty() ? question.trim() : String.join("\n", queries);
    }

    public List<String> queries(String question, List<Turn> history) {
        String trimmed = question == null ? "" : question.trim();
        boolean expanded = "expanded".equalsIgnoreCase(properties.getRewrite().getMode());
        String normalized = expanded ? RuleQuery.normalize(trimmed) : trimmed;
        List<String> parts = expanded ? RuleQuery.splitIntents(normalized) : List.of(normalized);
        if (history == null || history.isEmpty()) {
            return parts;
        }
        List<String> queries = new ArrayList<>();
        queries.add(rewriteWithHistory(parts.get(0), history));
        if (parts.size() > 1) {
            queries.addAll(parts.subList(1, parts.size()));
        }
        return queries;
    }

    private String rewriteWithHistory(String question, List<Turn> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("此前只保留问题和依据，不含回答正文：\n");
        for (Turn turn : history) {
            prompt.append("问：").append(turn.question()).append('\n');
            if (turn.evidence() != null && !turn.evidence().isBlank()) {
                prompt.append("依据：").append(turn.evidence()).append('\n');
            }
        }
        prompt.append("最新问题：").append(question.trim());
        String rewritten = chatClient.prompt()
                .system("把最新问题改写成一句独立的检索查询，补全省略的指代。口语、缩写和多跳条件都保留。保持原语言。只输出查询本身，不要回答问题。")
                .user(prompt.toString())
                .call()
                .content();
        if (rewritten == null || rewritten.isBlank()) {
            return question.trim();
        }
        return rewritten.trim().replace("\"", "");
    }
}
