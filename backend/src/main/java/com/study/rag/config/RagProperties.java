package com.study.rag.config;

import com.study.rag.retrieve.EvidenceOrganizer;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private final Index index = new Index();
    private final Elasticsearch elasticsearch = new Elasticsearch();
    private final Upload upload = new Upload();
    private final Chunk chunk = new Chunk();
    private final Embedding embedding = new Embedding();
    private final Retrieval retrieval = new Retrieval();
    private final Rerank rerank = new Rerank();
    private final Compression compression = new Compression();
    private final Memory memory = new Memory();
    private final Rewrite rewrite = new Rewrite();

    public Index getIndex() {
        return index;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public Upload getUpload() {
        return upload;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Rerank getRerank() {
        return rerank;
    }

    public Compression getCompression() {
        return compression;
    }

    public Memory getMemory() {
        return memory;
    }

    public Rewrite getRewrite() {
        return rewrite;
    }

    public static class Index {
        private String invertedIndex = "local";

        public String getInvertedIndex() {
            return invertedIndex;
        }

        public void setInvertedIndex(String invertedIndex) {
            this.invertedIndex = invertedIndex;
        }
    }

    public static class Elasticsearch {
        private String chunkIndex = "rag-chunks";
        private String documentIndex = "rag-documents";

        public String getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(String chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getDocumentIndex() {
            return documentIndex;
        }

        public void setDocumentIndex(String documentIndex) {
            this.documentIndex = documentIndex;
        }
    }

    public static class Upload {
        private long maxBytes = 50L * 1024 * 1024;
        private int maxChunks = 2000;

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        public int getMaxChunks() {
            return maxChunks;
        }

        public void setMaxChunks(int maxChunks) {
            this.maxChunks = maxChunks;
        }
    }

    public static class Chunk {
        private int targetChars = 800;
        private int overlapChars = 120;
        private int minChars = 20;
        private String strategy = "structured";

        public int getTargetChars() {
            return targetChars;
        }

        public void setTargetChars(int targetChars) {
            this.targetChars = targetChars;
        }

        public int getOverlapChars() {
            return overlapChars;
        }

        public void setOverlapChars(int overlapChars) {
            this.overlapChars = overlapChars;
        }

        public int getMinChars() {
            return minChars;
        }

        public void setMinChars(int minChars) {
            this.minChars = minChars;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
    }

    public static class Embedding {
        private String model = "qwen3.7-text-embedding-flash";
        private int dimensions = 1024;
        private int batchSize = 10;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Retrieval {
        private int bm25Size = 20;
        private int knnK = 20;
        private int knnNumCandidates = 100;
        private int rrfRankConstant = 60;
        private int rerankInputSize = 20;
        private int topK = 5;

        public int getBm25Size() {
            return bm25Size;
        }

        public void setBm25Size(int bm25Size) {
            this.bm25Size = bm25Size;
        }

        public int getKnnK() {
            return knnK;
        }

        public void setKnnK(int knnK) {
            this.knnK = knnK;
        }

        public int getKnnNumCandidates() {
            return knnNumCandidates;
        }

        public void setKnnNumCandidates(int knnNumCandidates) {
            this.knnNumCandidates = knnNumCandidates;
        }

        public int getRrfRankConstant() {
            return rrfRankConstant;
        }

        public void setRrfRankConstant(int rrfRankConstant) {
            this.rrfRankConstant = rrfRankConstant;
        }

        public int getRerankInputSize() {
            return rerankInputSize;
        }

        public void setRerankInputSize(int rerankInputSize) {
            this.rerankInputSize = rerankInputSize;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    public static class Rerank {
        private String provider = "dashscope";
        private String model = "qwen3.7-text-rerank";
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";
        private String path = "/services/rerank/text-rerank/text-rerank";
        private String teiUrl = "http://localhost:8081/rerank";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTeiUrl() {
            return teiUrl;
        }

        public void setTeiUrl(String teiUrl) {
            this.teiUrl = teiUrl;
        }
    }

    public static class Compression {
        private boolean enabled = true;
        private int neighborSentences = 1;
        private int llmMinChars = 480;
        private double dedupJaccard = 0.82;
        private double highScoreRatio = EvidenceOrganizer.DEFAULT_HIGH_RATIO;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getNeighborSentences() {
            return neighborSentences;
        }

        public void setNeighborSentences(int neighborSentences) {
            this.neighborSentences = neighborSentences;
        }

        public int getLlmMinChars() {
            return llmMinChars;
        }

        public void setLlmMinChars(int llmMinChars) {
            this.llmMinChars = llmMinChars;
        }

        public double getDedupJaccard() {
            return dedupJaccard;
        }

        public void setDedupJaccard(double dedupJaccard) {
            this.dedupJaccard = dedupJaccard;
        }

        public double getHighScoreRatio() {
            return highScoreRatio;
        }

        public void setHighScoreRatio(double highScoreRatio) {
            this.highScoreRatio = highScoreRatio;
        }
    }

    public static class Memory {
        private int maxTurns = 6;

        public int getMaxTurns() {
            return maxTurns;
        }

        public void setMaxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
        }
    }

    public static class Rewrite {
        private String mode = "baseline";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}
