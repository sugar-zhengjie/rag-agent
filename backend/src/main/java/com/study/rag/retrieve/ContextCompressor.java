package com.study.rag.retrieve;

import com.study.rag.config.RagProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContextCompressor {

    private static final String SYSTEM = """
            你在为当前问题保留证据，不是在概括整段材料。
            只输出片段里已经写明、并且和问题有关的内容。没有依据就写“无”。
            按下面四行输出，不要加别的说明：
            条件：
            结论：
            例外：
            原句：
            """;

    private final RagProperties properties;
    private final ChatClient chatClient;

    public ContextCompressor(RagProperties properties, ChatClient chatClient) {
        this.properties = properties;
        this.chatClient = chatClient;
    }

    public CompressionOutcome compress(String query, List<RankedChunk> reranked) {
        if (reranked == null || reranked.isEmpty()) {
            return new CompressionOutcome(List.of(), EvidenceOrganizer.detect(query).label(), null);
        }
        RagProperties.Compression compression = properties.getCompression();
        if (!compression.isEnabled()) {
            return outcome(query, reranked, compression.getHighScoreRatio(), null);
        }
        List<RankedChunk> unique = EvidenceSelector.dedup(reranked, compression.getDedupJaccard());
        List<RankedChunk> extracted = new ArrayList<>();
        String warning = null;
        for (RankedChunk chunk : unique) {
            String evidence = EvidenceSelector.select(chunk.content(), query, compression.getNeighborSentences());
            if (evidence.isBlank()) {
                continue;
            }
            if (evidence.length() >= compression.getLlmMinChars()) {
                try {
                    String structured = extract(query, evidence);
                    if (!structured.isBlank()) {
                        evidence = structured;
                    }
                } catch (RuntimeException ex) {
                    warning = "长片段压缩失败，已保留抽出的原句: " + ex.getMessage();
                }
            }
            extracted.add(withContent(chunk, evidence));
        }
        if (extracted.isEmpty()) {
            extracted.add(reranked.get(0));
            String fallback = "规则过滤没有留下和问题相关的原句，保留精排得分最高的一条";
            warning = warning == null ? fallback : warning + "；" + fallback;
        }
        return outcome(query, extracted, compression.getHighScoreRatio(), warning);
    }

    private static CompressionOutcome outcome(String query, List<RankedChunk> chunks, double highScoreRatio, String warning) {
        return new CompressionOutcome(
                EvidenceOrganizer.organize(query, chunks, highScoreRatio),
                EvidenceOrganizer.detect(query).label(),
                warning);
    }

    private String extract(String query, String evidence) {
        String raw = chatClient.prompt()
                .system(SYSTEM)
                .user("问题：" + query + "\n\n片段：\n" + evidence)
                .call()
                .content();
        if (raw == null || raw.isBlank() || raw.lines().allMatch(line -> line.endsWith("无") || line.equals("无"))) {
            return "";
        }
        return raw.trim();
    }

    static RankedChunk withContent(RankedChunk source, String content) {
        return source.withContent(content);
    }

    public record CompressionOutcome(List<RankedChunk> chunks, String layout, String warning) {
    }
}
