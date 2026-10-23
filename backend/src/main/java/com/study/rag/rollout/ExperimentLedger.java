package com.study.rag.rollout;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExperimentLedger {

    private final PipelineCatalog catalog;
    private PipelineSnapshot accepted;
    private final List<PipelineSnapshot> rounds = new ArrayList<>();

    public ExperimentLedger(PipelineCatalog catalog) {
        this.catalog = catalog;
    }

    @PostConstruct
    public void recordBaseline() {
        accepted = catalog.current();
        rounds.add(accepted);
    }

    public PipelineSnapshot accepted() {
        return accepted;
    }

    public List<String> diff(PipelineSnapshot proposed) {
        return changed(accepted, proposed);
    }

    public PipelineSnapshot accept(PipelineSnapshot proposed) {
        requireSmallChange(accepted, proposed);
        accepted = proposed;
        rounds.add(proposed);
        return proposed;
    }

    public static void requireSmallChange(PipelineSnapshot left, PipelineSnapshot right) {
        List<String> changed = changed(left, right);
        if (changed.size() > 2) {
            throw new IllegalArgumentException("每一轮只改变少量变量：" + String.join("、", changed));
        }
    }

    public static List<String> changed(PipelineSnapshot left, PipelineSnapshot right) {
        List<String> changed = new ArrayList<>();
        if (left == null || right == null) {
            return List.of("parser", "chunk", "embedding", "index", "queryRewrite", "rerank", "prompt");
        }
        add(changed, "parser", left.parser(), right.parser());
        add(changed, "chunk", left.chunk(), right.chunk());
        add(changed, "embedding", left.embedding(), right.embedding());
        add(changed, "index", left.index(), right.index());
        add(changed, "queryRewrite", left.queryRewrite(), right.queryRewrite());
        add(changed, "rerank", left.rerank(), right.rerank());
        add(changed, "prompt", left.prompt(), right.prompt());
        return changed;
    }

    private static void add(List<String> changed, String name, String left, String right) {
        if (left == null ? right != null : !left.equals(right)) {
            changed.add(name);
        }
    }
}
