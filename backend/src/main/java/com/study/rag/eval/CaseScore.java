package com.study.rag.eval;

import java.util.List;

public record CaseScore(
        String id,
        String category,
        String answerType,
        String question,
        Double hitRate,
        Double mrr,
        Double contextRecall,
        Double contextPrecision,
        Double metadataHit,
        Double faithfulness,
        Double answerRelevancy,
        Double citationAccuracy,
        Long retrievalMs,
        Long rerankMs,
        Long generationMs,
        int contextChars,
        String retrievalStage,
        String generationStage,
        List<String> retrievalNotes,
        List<String> generationNotes) {
}
