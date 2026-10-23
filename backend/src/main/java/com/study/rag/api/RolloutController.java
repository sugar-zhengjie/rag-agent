package com.study.rag.api;

import com.study.rag.ingest.ChunkStrategies;
import com.study.rag.rollout.ExperimentLedger;
import com.study.rag.rollout.FailureLog;
import com.study.rag.rollout.GrayLog;
import com.study.rag.rollout.PipelineCatalog;
import com.study.rag.rollout.PipelineSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RolloutController {

    private final PipelineCatalog catalog;
    private final ExperimentLedger ledger;
    private final FailureLog failureLog;
    private final GrayLog grayLog;

    public RolloutController(PipelineCatalog catalog, ExperimentLedger ledger, FailureLog failureLog, GrayLog grayLog) {
        this.catalog = catalog;
        this.ledger = ledger;
        this.failureLog = failureLog;
        this.grayLog = grayLog;
    }

    @GetMapping("/api/pipeline")
    public PipelineSnapshot pipeline() {
        return catalog.current();
    }

    @PostMapping("/api/experiments/check")
    public ExperimentView check(@RequestBody VersionOverride override) {
        PipelineSnapshot proposed = catalog.current().with(override.parser(), override.chunk(), override.embedding(),
                override.index(), override.queryRewrite(), override.rerank(), override.prompt());
        return new ExperimentView(proposed, ledger.diff(proposed));
    }

    @PostMapping("/api/experiments/accept")
    public PipelineSnapshot accept(@RequestBody VersionOverride override) {
        PipelineSnapshot proposed = catalog.current().with(override.parser(), override.chunk(), override.embedding(),
                override.index(), override.queryRewrite(), override.rerank(), override.prompt());
        return ledger.accept(proposed);
    }

    @PostMapping("/api/chunks/compare")
    public List<ChunkStrategies.StrategyHit> compare(@RequestBody CompareRequest request) {
        return ChunkStrategies.compare(request.text(), request.evidence());
    }

    @GetMapping("/api/eval/failures")
    public List<FailureLog.FailureSample> failures() {
        return failureLog.samples();
    }

    @GetMapping("/api/gray")
    public GrayLog.GraySummary gray() {
        return grayLog.summary();
    }

    public record VersionOverride(
            String parser,
            String chunk,
            String embedding,
            String index,
            String queryRewrite,
            String rerank,
            String prompt) {
    }

    public record ExperimentView(PipelineSnapshot proposed, List<String> changed) {
    }

    public record CompareRequest(String text, List<String> evidence) {
    }
}
