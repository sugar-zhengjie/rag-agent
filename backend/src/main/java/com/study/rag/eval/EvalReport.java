package com.study.rag.eval;

import com.study.rag.rollout.PipelineSnapshot;

import java.util.List;
import java.util.Map;

public record EvalReport(
        int cases,
        Map<String, Double> retrieval,
        Map<String, Double> generation,
        Map<String, Object> engineering,
        List<CaseScore> results,
        List<String> humanReview,
        String judgeNote,
        PipelineSnapshot pipeline) {
}
