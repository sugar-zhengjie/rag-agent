package com.study.rag.eval;

import com.study.rag.chat.RagChatService;
import com.study.rag.retrieve.HybridRetriever;
import com.study.rag.retrieve.RetrievalResult;
import com.study.rag.rollout.FailureLog;
import com.study.rag.rollout.GrayLog;
import com.study.rag.rollout.PipelineCatalog;
import com.study.rag.rollout.PipelineSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvalService {

    private final EvalDataset dataset;
    private final HybridRetriever retriever;
    private final RagChatService chatService;
    private final PipelineCatalog pipelineCatalog;
    private final FailureLog failureLog;
    private final GrayLog grayLog;

    public EvalService(EvalDataset dataset, HybridRetriever retriever, RagChatService chatService,
                       PipelineCatalog pipelineCatalog, FailureLog failureLog, GrayLog grayLog) {
        this.dataset = dataset;
        this.retriever = retriever;
        this.chatService = chatService;
        this.pipelineCatalog = pipelineCatalog;
        this.failureLog = failureLog;
        this.grayLog = grayLog;
    }

    public List<EvalCase> cases() {
        return dataset.cases();
    }

    public CaseScore score(String caseId, List<String> ranked, List<String> context, List<String> metadataTexts,
                           String answer, int topK, Long retrievalMs, Long rerankMs, Long generationMs) {
        EvalCase item = dataset.cases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("评估样本不存在: " + caseId));
        return RagScorer.score(item, ranked, context, metadataTexts, answer, topK, retrievalMs, rerankMs, generationMs);
    }

    public EvalReport run(String category, int limit, int topK, boolean generate) {
        List<CaseScore> scores = new ArrayList<>();
        int kept = 0;
        for (EvalCase item : dataset.cases()) {
            if (category != null && !category.isBlank() && !category.equals(item.category())) {
                continue;
            }
            if (kept >= limit) {
                break;
            }
            kept++;
            RetrievalResult result = retriever.retrieve(item.question(), "eval-" + item.id(), topK, com.study.rag.acl.DatasetCatalog.OWNER_ID);
            String answer = null;
            Long generationMs = null;
            if (generate && result.compressed() != null && !result.compressed().isEmpty()) {
                RagChatService.GeneratedAnswer generated = chatService.generate(result);
                answer = generated.answer();
                generationMs = generated.elapsedMs();
            }
            var timing = result.timing();
            scores.add(RagScorer.score(
                    item,
                    RagScorer.contents(result.reranked()),
                    RagScorer.contents(result.compressed()),
                    RagScorer.metadataText(result.reranked()),
                    answer,
                    topK,
                    timing == null ? null : timing.retrievalMs(),
                    timing == null ? null : timing.rerankMs(),
                    generationMs));
        }
        EvalReport report = RagScorer.aggregate(scores);
        PipelineSnapshot pipeline = pipelineCatalog.current();
        failureLog.record(scores, pipeline);
        grayLog.rememberOffline(pipeline.key(), report.retrieval().get("hitRate"), report.retrieval().get("contextRecall"));
        return new EvalReport(report.cases(), report.retrieval(), report.generation(), report.engineering(),
                report.results(), report.humanReview(), report.judgeNote(), pipeline);
    }
}
