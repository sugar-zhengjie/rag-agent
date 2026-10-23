package com.study.rag.retrieve;

public record RankedChunk(
        String chunkId,
        String docId,
        String fileName,
        String fileType,
        Integer page,
        String sheet,
        String heading,
        String content,
        int chunkIndex,
        double score,
        long createdAt,
        String version,
        String section,
        String conflictNote,
        String parentContent) {

    public RankedChunk(String chunkId, String docId, String fileName, String fileType, Integer page, String sheet,
                       String heading, String content, int chunkIndex, double score, long createdAt, String version,
                       String section, String conflictNote) {
        this(chunkId, docId, fileName, fileType, page, sheet, heading, content, chunkIndex, score, createdAt, version,
                section, conflictNote, null);
    }

    public RankedChunk withScore(double newScore) {
        return new RankedChunk(chunkId, docId, fileName, fileType, page, sheet, heading, content, chunkIndex,
                newScore, createdAt, version, section, conflictNote, parentContent);
    }

    public RankedChunk withContent(String newContent) {
        return new RankedChunk(chunkId, docId, fileName, fileType, page, sheet, heading, newContent, chunkIndex,
                score, createdAt, version, section, conflictNote, parentContent);
    }

    public RankedChunk withMeta(long newCreatedAt, String newVersion) {
        return new RankedChunk(chunkId, docId, fileName, fileType, page, sheet, heading, content, chunkIndex,
                score, newCreatedAt, newVersion, section, conflictNote, parentContent);
    }

    public RankedChunk withOrganization(String newSection, String newConflictNote) {
        return new RankedChunk(chunkId, docId, fileName, fileType, page, sheet, heading, content, chunkIndex,
                score, createdAt, version, newSection, newConflictNote, parentContent);
    }
}
