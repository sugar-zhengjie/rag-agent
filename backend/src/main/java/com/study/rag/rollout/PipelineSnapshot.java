package com.study.rag.rollout;

public record PipelineSnapshot(
        String parser,
        String chunk,
        String embedding,
        String index,
        String queryRewrite,
        String rerank,
        String prompt) {

    public String key() {
        return parser + "|" + chunk + "|" + embedding + "|" + index + "|" + queryRewrite + "|" + rerank + "|" + prompt;
    }

    public PipelineSnapshot with(String parser, String chunk, String embedding, String index, String queryRewrite,
                                 String rerank, String prompt) {
        return new PipelineSnapshot(
                blank(parser) ? this.parser : parser,
                blank(chunk) ? this.chunk : chunk,
                blank(embedding) ? this.embedding : embedding,
                blank(index) ? this.index : index,
                blank(queryRewrite) ? this.queryRewrite : queryRewrite,
                blank(rerank) ? this.rerank : rerank,
                blank(prompt) ? this.prompt : prompt);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
