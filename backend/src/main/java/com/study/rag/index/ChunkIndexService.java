package com.study.rag.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.study.rag.acl.PreparedSearch;
import com.study.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkIndexService {

    private static final Logger log = LoggerFactory.getLogger(ChunkIndexService.class);

    private final ElasticsearchClient client;
    private final RagProperties properties;
    private final boolean localInvertedIndex;
    private final LocalChunkStore localStore = new LocalChunkStore();

    public ChunkIndexService(ObjectProvider<ElasticsearchClient> clients, RagProperties properties) {
        this.client = clients.getIfAvailable();
        this.properties = properties;
        this.localInvertedIndex = !"elasticsearch".equalsIgnoreCase(properties.getIndex().getInvertedIndex());
    }

    public String invertedIndex() {
        return localInvertedIndex ? "local" : "elasticsearch";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void prepareOnStartup() {
        if (localInvertedIndex) {
            log.info("local inverted index ready");
            return;
        }
        try {
            ensureIndexes();
            log.info("elasticsearch indexes ready: {}, {}", chunkIndex(), documentIndex());
        } catch (Exception ex) {
            log.warn("elasticsearch is not ready yet: {}", ex.getMessage());
        }
    }

    public boolean ping() {
        if (localInvertedIndex || client == null) {
            return false;
        }
        try {
            return client.ping().value();
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean chunkIndexExists() {
        if (localInvertedIndex) {
            return true;
        }
        try {
            return client.indices().exists(e -> e.index(chunkIndex())).value();
        } catch (Exception ex) {
            return false;
        }
    }

    public void ensureIndexes() {
        if (localInvertedIndex) {
            return;
        }
        if (client == null) {
            throw new IllegalStateException("未配置 Elasticsearch 客户端");
        }
        try {
            if (!client.indices().exists(e -> e.index(chunkIndex())).value()) {
                createChunkIndex();
            }
            if (!client.indices().exists(e -> e.index(documentIndex())).value()) {
                createDocumentIndex();
            }
            putDatasetField(chunkIndex());
            putDatasetField(documentIndex());
        } catch (ElasticsearchException ex) {
            if (alreadyExists(ex)) {
                return;
            }
            throw new IllegalStateException("创建索引失败: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new IllegalStateException("连接 Elasticsearch 失败: " + ex.getMessage(), ex);
        }
    }

    public void index(StoredDocument document, List<ChunkDocument> chunks) {
        if (localInvertedIndex) {
            localStore.index(document, chunks);
            return;
        }
        ensureIndexes();
        try {
            BulkResponse response = client.bulk(bulk -> {
                bulk.refresh(Refresh.True);
                for (ChunkDocument chunk : chunks) {
                    bulk.operations(op -> op.index(idx -> idx
                            .index(chunkIndex())
                            .id(chunk.getChunkId())
                            .document(chunk)));
                }
                return bulk;
            });
            if (response.errors()) {
                throw new IllegalStateException("写入切片失败: " + firstBulkError(response));
            }
            client.index(req -> req
                    .index(documentIndex())
                    .id(document.getDocId())
                    .refresh(Refresh.True)
                    .document(document));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("写入 Elasticsearch 失败: " + ex.getMessage(), ex);
        }
    }

    public List<StoredDocument> listDocuments(List<String> datasetIds) {
        PreparedSearch search = PreparedSearch.prepare(datasetIds);
        if (!search.execute()) {
            return List.of();
        }
        if (localInvertedIndex) {
            return localStore.listDocuments(search.datasetIds());
        }
        ensureIndexes();
        try {
            SearchResponse<StoredDocument> response = client.search(s -> s
                            .index(documentIndex())
                            .size(200)
                            .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                            .query(q -> q.terms(t -> t.field("datasetId").terms(v -> v.value(fieldValues(search.datasetIds()))))),
                    StoredDocument.class);
            List<StoredDocument> documents = new ArrayList<>();
            for (Hit<StoredDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    documents.add(hit.source());
                }
            }
            return documents;
        } catch (IOException ex) {
            throw new IllegalStateException("读取文档列表失败: " + ex.getMessage(), ex);
        }
    }

    public StoredDocument findDocument(String docId) {
        if (localInvertedIndex) {
            return localStore.findDocument(docId);
        }
        ensureIndexes();
        try {
            var response = client.get(g -> g.index(documentIndex()).id(docId), StoredDocument.class);
            return response.found() ? response.source() : null;
        } catch (IOException ex) {
            throw new IllegalStateException("读取文档失败: " + ex.getMessage(), ex);
        }
    }

    public List<ChunkDocument> listChunks(String docId) {
        if (localInvertedIndex) {
            return localStore.listChunks(docId);
        }
        ensureIndexes();
        try {
            SearchResponse<ChunkDocument> response = client.search(s -> s
                            .index(chunkIndex())
                            .size(2000)
                            .source(src -> src.filter(f -> f.excludes("embedding")))
                            .sort(sort -> sort.field(f -> f.field("chunkIndex").order(SortOrder.Asc)))
                            .query(q -> q.term(t -> t.field("docId").value(docId))),
                    ChunkDocument.class);
            List<ChunkDocument> chunks = new ArrayList<>();
            for (Hit<ChunkDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    chunks.add(hit.source());
                }
            }
            return chunks;
        } catch (IOException ex) {
            throw new IllegalStateException("读取切片失败: " + ex.getMessage(), ex);
        }
    }

    public boolean deleteDocument(String docId) {
        if (localInvertedIndex) {
            return localStore.deleteDocument(docId);
        }
        ensureIndexes();
        try {
            var deleted = client.delete(d -> d.index(documentIndex()).id(docId).refresh(Refresh.True));
            DeleteByQueryResponse chunks = client.deleteByQuery(d -> d
                    .index(chunkIndex())
                    .refresh(true)
                    .query(q -> q.term(t -> t.field("docId").value(docId))));
            boolean removedCatalog = deleted.result() != null && !"not_found".equals(deleted.result().jsonValue());
            long removedChunks = chunks.deleted() == null ? 0 : chunks.deleted();
            return removedCatalog || removedChunks > 0;
        } catch (IOException ex) {
            throw new IllegalStateException("删除文档失败: " + ex.getMessage(), ex);
        }
    }

    public List<ChunkHit> searchBm25(String query, int size, List<String> datasetIds) {
        PreparedSearch search = PreparedSearch.prepare(datasetIds);
        if (!search.execute()) {
            return List.of();
        }
        if (localInvertedIndex) {
            return localStore.searchBm25(query, size, search.datasetIds());
        }
        ensureIndexes();
        try {
            SearchResponse<ChunkDocument> response = client.search(s -> s
                            .index(chunkIndex())
                            .size(size)
                            .source(src -> src.filter(f -> f.excludes("embedding")))
                            .query(q -> q.bool(b -> b
                                    .must(m -> m.match(mm -> mm.field("content").query(query)))
                                    .filter(f -> f.terms(t -> t.field("datasetId").terms(v -> v.value(fieldValues(search.datasetIds()))))))),
                    ChunkDocument.class);
            return toHits(response);
        } catch (IOException ex) {
            throw new IllegalStateException("BM25 检索失败: " + ex.getMessage(), ex);
        }
    }

    public List<ChunkHit> searchKnn(List<Float> vector, int k, int numCandidates, List<String> datasetIds) {
        PreparedSearch search = PreparedSearch.prepare(datasetIds);
        if (!search.execute()) {
            return List.of();
        }
        if (localInvertedIndex) {
            return localStore.searchKnn(vector, k, search.datasetIds());
        }
        ensureIndexes();
        try {
            SearchResponse<ChunkDocument> response = client.search(s -> s
                            .index(chunkIndex())
                            .size(k)
                            .source(src -> src.filter(f -> f.excludes("embedding")))
                            .knn(knn -> knn
                                    .field("embedding")
                                    .queryVector(vector)
                                    .k(k)
                                    .numCandidates(numCandidates)
                                    .filter(f -> f.terms(t -> t.field("datasetId").terms(v -> v.value(fieldValues(search.datasetIds())))))),
                    ChunkDocument.class);
            return toHits(response);
        } catch (IOException ex) {
            throw new IllegalStateException("向量检索失败: " + ex.getMessage(), ex);
        }
    }

    private void createChunkIndex() throws IOException {
        int dimensions = properties.getEmbedding().getDimensions();
        client.indices().create(c -> c
                .index(chunkIndex())
                .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                .mappings(m -> m
                        .properties("content", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                        .properties("embedding", p -> p.denseVector(d -> d
                                .dims(dimensions)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)
                                .indexOptions(io -> io.type(DenseVectorIndexOptionsType.Hnsw).m(16).efConstruction(100))))
                        .properties("chunkId", p -> p.keyword(k -> k))
                        .properties("docId", p -> p.keyword(k -> k))
                        .properties("datasetId", p -> p.keyword(k -> k))
                        .properties("fileName", p -> p.keyword(k -> k))
                        .properties("fileType", p -> p.keyword(k -> k))
                        .properties("page", p -> p.integer(i -> i))
                        .properties("sheet", p -> p.keyword(k -> k))
                        .properties("heading", p -> p.keyword(k -> k.ignoreAbove(512)))
                        .properties("chunkIndex", p -> p.integer(i -> i))
                        .properties("createdAt", p -> p.long_(l -> l))));
    }

    private void createDocumentIndex() throws IOException {
        client.indices().create(c -> c
                .index(documentIndex())
                .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                .mappings(m -> m
                        .properties("docId", p -> p.keyword(k -> k))
                        .properties("datasetId", p -> p.keyword(k -> k))
                        .properties("fileName", p -> p.keyword(k -> k))
                        .properties("fileType", p -> p.keyword(k -> k))
                        .properties("chunkCount", p -> p.integer(i -> i))
                        .properties("createdAt", p -> p.long_(l -> l))));
    }

    private void putDatasetField(String index) throws IOException {
        client.indices().putMapping(m -> m
                .index(index)
                .properties("datasetId", p -> p.keyword(k -> k)));
    }

    private static List<FieldValue> fieldValues(List<String> datasetIds) {
        return datasetIds.stream().map(FieldValue::of).toList();
    }

    private static List<ChunkHit> toHits(SearchResponse<ChunkDocument> response) {
        List<ChunkHit> hits = new ArrayList<>();
        for (Hit<ChunkDocument> hit : response.hits().hits()) {
            if (hit.source() == null) {
                continue;
            }
            double score = hit.score() == null ? 0d : hit.score();
            hits.add(new ChunkHit(hit.source(), score));
        }
        return hits;
    }

    private static String firstBulkError(BulkResponse response) {
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                return item.error().reason();
            }
        }
        return "未知批量写入错误";
    }

    private static boolean alreadyExists(ElasticsearchException ex) {
        String type = ex.error() == null ? "" : ex.error().type();
        return type != null && type.contains("resource_already_exists");
    }

    private String chunkIndex() {
        return properties.getElasticsearch().getChunkIndex();
    }

    private String documentIndex() {
        return properties.getElasticsearch().getDocumentIndex();
    }
}
