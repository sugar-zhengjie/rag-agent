package com.study.rag.rollout;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class GrayLog {

    private final List<Long> retrievalMillis = new ArrayList<>();
    private int requests;
    private int refusals;
    private Double offlineHitRate;
    private Double offlineContextRecall;
    private String pipelineKey = "";

    public synchronized void observe(String pipelineKey, long retrievalMs, boolean refused) {
        this.pipelineKey = pipelineKey;
        requests++;
        if (refused) {
            refusals++;
        }
        if (retrievalMs > 0) {
            retrievalMillis.add(retrievalMs);
        }
    }

    public synchronized void rememberOffline(String pipelineKey, Double hitRate, Double contextRecall) {
        this.pipelineKey = pipelineKey;
        this.offlineHitRate = hitRate;
        this.offlineContextRecall = contextRecall;
    }

    public synchronized GraySummary summary() {
        return new GraySummary(pipelineKey, requests, refusals, percentile(), offlineHitRate, offlineContextRecall,
                "离线评估的收益还要在灰度请求里继续观察");
    }

    private Long percentile() {
        if (retrievalMillis.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(retrievalMillis);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, index));
    }

    public record GraySummary(
            String pipelineKey,
            int requests,
            int refusals,
            Long p95RetrievalMs,
            Double offlineHitRate,
            Double offlineContextRecall,
            String note) {
    }
}
