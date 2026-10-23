package com.study.rag.chat;

import com.study.rag.retrieve.EvidenceOrganizer;
import com.study.rag.retrieve.HybridRetriever;
import com.study.rag.retrieve.RankedChunk;
import com.study.rag.retrieve.RetrievalResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagChatService {

    public static final String PROMPT_VERSION = "cite-refuse-v1";

    private static final String SYSTEM = """
            你是一个依据资料回答问题的助手。引用已经按当前问题抽出证据，并按问题类型排好，不是召回通道的原始顺序。
            高置信度证据在前。低置信度单独成段，不要把它写成确定事实。
            同一文档的片段保持原文顺序。对比题按主题阅读，时间题按时间线阅读，排障题按现象、原因、处理步骤、注意事项阅读。
            若片段标注了冲突，回答时写出版本和入库时间的差异，不要合成一条结论。
            只根据这些引用回答，不要编造资料里没有的内容。
            资料不足时直接说明资料中没有相关内容。
            回答使用中文，并在对应句子后用 [编号] 标出引用。
            """;

    private final HybridRetriever retriever;
    private final ConversationMemory memory;
    private final ChatClient chatClient;

    public RagChatService(HybridRetriever retriever, ConversationMemory memory, ChatClient chatClient) {
        this.retriever = retriever;
        this.memory = memory;
        this.chatClient = chatClient;
    }

    public ChatOutcome ask(String question, String sessionId, Integer topK, String userId) {
        RetrievalResult retrieval = retriever.retrieve(question, sessionId, topK, userId);
        GeneratedAnswer generated = generate(retrieval);
        memory.append(userId, sessionId, question.trim(), evidenceLine(retrieval.compressed()));
        return new ChatOutcome(generated.answer(), retrieval);
    }

    public GeneratedAnswer generate(RetrievalResult retrieval) {
        List<RankedChunk> citations = retrieval.compressed();
        if (citations == null || citations.isEmpty()) {
            return new GeneratedAnswer("资料中没有检索到相关内容。", 0);
        }
        long started = System.nanoTime();
        String answer = chatClient.prompt()
                .system(SYSTEM)
                .user(userPrompt(retrieval.rewrittenQuery(), citations))
                .call()
                .content();
        long elapsed = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        if (answer == null || answer.isBlank()) {
            answer = "模型没有返回内容。";
        }
        return new GeneratedAnswer(answer.trim(), elapsed);
    }

    private static String userPrompt(String question, List<RankedChunk> citations) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("问题：").append(question).append("\n\n引用：\n");
        String section = null;
        for (int i = 0; i < citations.size(); i++) {
            RankedChunk chunk = citations.get(i);
            if (chunk.section() != null && !chunk.section().equals(section)) {
                section = chunk.section();
                prompt.append('\n').append(section).append('\n');
            }
            prompt.append('[').append(i + 1).append("] 来源：");
            prompt.append(chunk.fileName() == null ? "未命名" : chunk.fileName());
            if (chunk.page() != null) {
                prompt.append(" 第").append(chunk.page()).append("页");
            }
            if (chunk.sheet() != null && !chunk.sheet().isBlank()) {
                prompt.append(" 表 ").append(chunk.sheet());
            }
            if (chunk.heading() != null && !chunk.heading().isBlank()) {
                prompt.append(" / ").append(chunk.heading());
            }
            if (chunk.version() != null && !chunk.version().isBlank()) {
                prompt.append(" 版本 ").append(chunk.version());
            }
            if (chunk.createdAt() > 0) {
                prompt.append(" 入库 ").append(EvidenceOrganizer.formatTime(chunk.createdAt()));
            }
            if (chunk.conflictNote() != null && !chunk.conflictNote().isBlank()) {
                prompt.append("\n冲突：").append(chunk.conflictNote());
            }
            prompt.append('\n').append(chunk.content()).append("\n\n");
        }
        return prompt.toString();
    }

    private static String evidenceLine(List<RankedChunk> citations) {
        if (citations.isEmpty()) {
            return "无检索证据";
        }
        StringBuilder line = new StringBuilder();
        for (RankedChunk chunk : citations) {
            if (!line.isEmpty()) {
                line.append("；");
            }
            line.append(chunk.fileName() == null ? "未命名" : chunk.fileName());
            if (chunk.heading() != null && !chunk.heading().isBlank()) {
                line.append(" / ").append(chunk.heading());
            } else if (chunk.page() != null) {
                line.append(" 第").append(chunk.page()).append("页");
            }
            if (line.length() > 160) {
                break;
            }
        }
        return line.toString();
    }

    public record ChatOutcome(String answer, RetrievalResult retrieval) {
    }

    public record GeneratedAnswer(String answer, long elapsedMs) {
    }
}
