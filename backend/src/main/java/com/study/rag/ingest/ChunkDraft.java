package com.study.rag.ingest;

public record ChunkDraft(String content, Integer page, String sheet, String heading, int chunkIndex, String parentContent) {

    public ChunkDraft(String content, Integer page, String sheet, String heading, int chunkIndex) {
        this(content, page, sheet, heading, chunkIndex, null);
    }
}
