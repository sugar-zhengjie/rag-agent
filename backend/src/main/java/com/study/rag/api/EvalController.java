package com.study.rag.api;

import com.study.rag.eval.CaseScore;
import com.study.rag.eval.EvalCase;
import com.study.rag.eval.EvalReport;
import com.study.rag.eval.EvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/api/eval/dataset")
    public List<EvalCase> dataset() {
        return evalService.cases();
    }

    @PostMapping("/api/eval/score")
    public CaseScore score(@RequestBody ScoreRequest request) {
        int topK = request.topK() == null ? 5 : request.topK();
        return evalService.score(request.caseId(), request.ranked(), request.context(), request.metadataTexts(),
                request.answer(), topK, request.retrievalMs(), request.rerankMs(), request.generationMs());
    }

    @PostMapping("/api/eval/run")
    public EvalReport run(@RequestBody(required = false) RunRequest request) {
        RunRequest body = request == null ? new RunRequest(null, null, null, null) : request;
        int limit = body.limit() == null ? 60 : Math.max(1, Math.min(body.limit(), 100));
        int topK = body.topK() == null ? 5 : Math.max(1, Math.min(body.topK(), 20));
        boolean generate = Boolean.TRUE.equals(body.generate());
        return evalService.run(body.category(), limit, topK, generate);
    }

    public record ScoreRequest(
            String caseId,
            List<String> ranked,
            List<String> context,
            List<String> metadataTexts,
            String answer,
            Integer topK,
            Long retrievalMs,
            Long rerankMs,
            Long generationMs) {
    }

    public record RunRequest(String category, Integer limit, Integer topK, Boolean generate) {
    }
}
