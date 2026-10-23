package com.study.rag.rollout;

import com.study.rag.eval.CaseScore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FailureLog {

    private final List<FailureSample> samples = new ArrayList<>();

    public synchronized void record(List<CaseScore> scores, PipelineSnapshot pipeline) {
        for (CaseScore score : scores) {
            boolean retrieval = "有问题".equals(score.retrievalStage());
            boolean generation = "有问题".equals(score.generationStage());
            if (!retrieval && !generation) {
                continue;
            }
            samples.add(new FailureSample(
                    score.id(),
                    score.category(),
                    score.question(),
                    score.retrievalStage(),
                    score.generationStage(),
                    pipeline.key()));
        }
    }

    public synchronized List<FailureSample> samples() {
        return List.copyOf(samples);
    }

    public record FailureSample(
            String id,
            String category,
            String question,
            String retrievalStage,
            String generationStage,
            String pipelineKey) {
    }
}
