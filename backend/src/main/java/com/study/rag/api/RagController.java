package com.study.rag.api;

import com.study.rag.acl.DatasetCatalog;
import com.study.rag.acl.WorkspaceUser;
import com.study.rag.api.dto.ApiModels.AskRequest;
import com.study.rag.api.dto.ApiModels.ChatResponse;
import com.study.rag.api.dto.ApiModels.ChunkView;
import com.study.rag.api.dto.ApiModels.DocumentView;
import com.study.rag.api.dto.ApiModels.HealthView;
import com.study.rag.api.dto.ApiModels.RetrievalView;
import com.study.rag.chat.ConversationMemory;
import com.study.rag.chat.RagChatService;
import com.study.rag.config.RagProperties;
import com.study.rag.index.ChunkIndexService;
import com.study.rag.index.StoredDocument;
import com.study.rag.ingest.IngestService;
import com.study.rag.retrieve.HybridRetriever;
import com.study.rag.retrieve.RetrievalResult;
import com.study.rag.rollout.GrayLog;
import com.study.rag.rollout.PipelineCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class RagController {

    private final IngestService ingestService;
    private final ChunkIndexService indexService;
    private final HybridRetriever retriever;
    private final RagChatService chatService;
    private final ConversationMemory memory;
    private final RagProperties properties;
    private final GrayLog grayLog;
    private final PipelineCatalog pipelineCatalog;
    private final DatasetCatalog datasetCatalog;
    private final String apiKey;
    private final String chatModel;
    private final String embeddingModel;
    private final String rerankModel;

    public RagController(IngestService ingestService,
                         ChunkIndexService indexService,
                         HybridRetriever retriever,
                         RagChatService chatService,
                         ConversationMemory memory,
                         RagProperties properties,
                         GrayLog grayLog,
                         PipelineCatalog pipelineCatalog,
                         DatasetCatalog datasetCatalog,
                         @Value("${spring.ai.dashscope.api-key:}") String apiKey,
                         @Value("${spring.ai.dashscope.chat.options.model:qwen-plus-latest}") String chatModel,
                         @Value("${spring.ai.dashscope.embedding.options.model:qwen3.7-text-embedding-flash}") String embeddingModel,
                         @Value("${spring.ai.dashscope.rerank.options.model:qwen3.7-text-rerank}") String rerankModel) {
        this.ingestService = ingestService;
        this.indexService = indexService;
        this.retriever = retriever;
        this.chatService = chatService;
        this.memory = memory;
        this.properties = properties;
        this.grayLog = grayLog;
        this.pipelineCatalog = pipelineCatalog;
        this.datasetCatalog = datasetCatalog;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.rerankModel = rerankModel;
    }

    @PostMapping(value = "/api/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView upload(@RequestHeader(value = "X-User-Id", required = false) String userId,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam("datasetId") String datasetId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件是空的");
        }
        WorkspaceUser user = datasetCatalog.user(userId);
        if (!datasetCatalog.canRead(user, datasetId)) {
            throw new IllegalArgumentException("没有该知识库的权限");
        }
        return ingestService.ingest(file.getOriginalFilename(), file.getBytes(), datasetId);
    }

    @GetMapping("/api/documents")
    public List<DocumentView> documents(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return indexService.listDocuments(datasetCatalog.visibleIds(userId)).stream().map(DocumentView::from).toList();
    }

    @GetMapping("/api/documents/{docId}/chunks")
    public List<ChunkView> chunks(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @PathVariable String docId) {
        requireVisibleDocument(userId, docId);
        List<ChunkView> chunks = indexService.listChunks(docId).stream().map(ChunkView::from).toList();
        if (chunks.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "文档不存在或没有切片");
        }
        return chunks;
    }

    @DeleteMapping("/api/documents/{docId}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @PathVariable String docId) {
        requireVisibleDocument(userId, docId);
        if (!indexService.deleteDocument(docId)) {
            throw new ResponseStatusException(NOT_FOUND, "文档不存在");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/retrieve")
    public RetrievalView retrieve(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                  @RequestBody AskRequest request) {
        RetrievalResult result = retriever.retrieve(request.question(), request.sessionId(), request.topK(), userId);
        long retrievalMs = result.timing() == null ? 0 : result.timing().retrievalMs();
        grayLog.observe(pipelineCatalog.current().key(), retrievalMs, false);
        return RetrievalView.from(result);
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@RequestHeader(value = "X-User-Id", required = false) String userId,
                             @RequestBody AskRequest request) {
        var outcome = chatService.ask(request.question(), request.sessionId(), request.topK(), userId);
        long retrievalMs = outcome.retrieval().timing() == null ? 0 : outcome.retrieval().timing().retrievalMs();
        grayLog.observe(pipelineCatalog.current().key(), retrievalMs, isRefusal(outcome.answer()));
        RetrievalView trace = RetrievalView.from(outcome.retrieval());
        return new ChatResponse(outcome.answer(), outcome.retrieval().rewrittenQuery(), trace.compressed(), trace);
    }

    @DeleteMapping("/api/sessions/{sessionId}")
    public ResponseEntity<Void> clearSession(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                             @PathVariable String sessionId) {
        memory.clear(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/health")
    public HealthView health() {
        boolean localIndex = "local".equals(indexService.invertedIndex());
        boolean elasticsearch = indexService.ping();
        return new HealthView(
                elasticsearch,
                localIndex || (elasticsearch && indexService.chunkIndexExists()),
                apiKey != null && !apiKey.isBlank(),
                embeddingModel,
                properties.getEmbedding().getDimensions(),
                chatModel,
                properties.getRerank().getProvider(),
                rerankModel,
                indexService.invertedIndex());
    }

    private static boolean isRefusal(String answer) {
        return answer != null && (answer.contains("资料中没有") || answer.contains("没有检索到"));
    }

    private void requireVisibleDocument(String userId, String docId) {
        StoredDocument document = indexService.findDocument(docId);
        if (document == null || !datasetCatalog.canRead(datasetCatalog.user(userId), document.getDatasetId())) {
            throw new ResponseStatusException(NOT_FOUND, "文档不存在");
        }
    }
}
