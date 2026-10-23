package com.study.rag.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownChunkerTest {

    @Test
    void keepsHeadingPathOnEachSection() {
        MarkdownChunker chunker = new MarkdownChunker(new RecursiveTextSplitter(800, 80, 1));
        String markdown = """
                # 总览
                这是总览正文。

                ## 安装
                安装步骤在这里。

                ```
                # 这不是标题
                ```

                ### 注意
                小心配置。
                """;

        List<ChunkDraft> chunks = chunker.chunk(markdown);

        assertEquals(3, chunks.size());
        assertEquals("总览", chunks.get(0).heading());
        assertTrue(chunks.get(0).content().startsWith("总览\n"));
        assertEquals("总览 / 安装", chunks.get(1).heading());
        assertTrue(chunks.get(1).content().contains("安装步骤在这里"));
        assertTrue(chunks.get(1).content().contains("# 这不是标题"));
        assertEquals("总览 / 安装 / 注意", chunks.get(2).heading());
    }
}
