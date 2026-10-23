package com.study.rag.eval;

import java.util.List;
import java.util.Map;

public record EvalCase(
        String id,
        String category,
        String question,
        String goldenAnswer,
        List<String> goldenContext,
        Map<String, String> metadataFilter,
        String answerType) {
}
