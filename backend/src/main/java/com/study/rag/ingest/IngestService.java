package com.study.rag.ingest;

import com.study.rag.api.dto.ApiModels.DocumentView;
import com.study.rag.config.RagProperties;
import com.study.rag.embed.EmbeddingClient;
import com.study.rag.index.ChunkDocument;
import com.study.rag.index.ChunkIndexService;
import com.study.rag.index.StoredDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final DocumentParser parser;
    private final EmbeddingClient embeddingClient;
    private final ChunkIndexService indexService;
    private final RagProperties properties;

    public IngestService(DocumentParser parser,
                         EmbeddingClient embeddingClient,
                         ChunkIndexService indexService,
                         RagProperties properties) {
        this.parser = parser;
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
        this.properties = properties;
    }

    public DocumentView ingest(String originalName, byte[] bytes, String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("请选择知识库");
        }
        String fileName = originalName == null || originalName.isBlank()
                ? "untitled.txt"
                : Paths.get(originalName).getFileName().toString();
        if (bytes.length > properties.getUpload().getMaxBytes()) {
            throw new IllegalArgumentException("文件超过 50MB");
        }
        String fileType = DocumentParser.extensionOf(fileName);
        List<ChunkDraft> drafts = parser.parse(fileName, bytes);
        String docId = UUID.randomUUID().toString();
        long createdAt = Instant.now().toEpochMilli();
        indexService.ensureIndexes();
        List<String> texts = drafts.stream().map(ChunkDraft::content).toList();
        List<float[]> vectors = embeddingClient.embedDocuments(texts);
        List<ChunkDocument> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            ChunkDocument chunk = new ChunkDocument();
            chunk.setChunkId(docId + "-" + draft.chunkIndex());
            chunk.setDocId(docId);
            chunk.setDatasetId(datasetId);
            chunk.setFileName(fileName);
            chunk.setFileType(fileType);
            chunk.setContent(draft.content());
            chunk.setPage(draft.page());
            chunk.setSheet(draft.sheet());
            chunk.setHeading(draft.heading());
            chunk.setChunkIndex(draft.chunkIndex());
            chunk.setCreatedAt(createdAt);
            chunk.setParentContent(draft.parentContent());
            chunk.assignEmbedding(vectors.get(i));
            chunks.add(chunk);
        }
        StoredDocument document = new StoredDocument();
        document.setDocId(docId);
        document.setDatasetId(datasetId);
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setChunkCount(chunks.size());
        document.setCreatedAt(createdAt);
        indexService.index(document, chunks);
        log.info("ingested {} as {} chunks ({})", fileName, chunks.size(), docId);
        return DocumentView.from(document);
    }
}
