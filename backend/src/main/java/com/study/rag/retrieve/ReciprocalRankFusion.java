package com.study.rag.retrieve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    public static <T> List<Fused<T>> fuse(List<List<T>> rankings, Function<T, String> idFn, int rankConstant) {
        if (rankConstant < 1) {
            throw new IllegalArgumentException("rankConstant 必须大于等于 1");
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, T> firstSeen = new HashMap<>();
        for (List<T> ranking : rankings) {
            Set<String> seen = new HashSet<>();
            int rank = 0;
            for (T item : ranking) {
                String id = idFn.apply(item);
                if (id == null || !seen.add(id)) {
                    continue;
                }
                rank++;
                firstSeen.putIfAbsent(id, item);
                double contribution = 1.0d / (rankConstant + rank);
                scores.merge(id, contribution, Double::sum);
            }
        }
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(scores.entrySet());
        ordered.sort(Comparator.comparingDouble((Map.Entry<String, Double> entry) -> entry.getValue()).reversed());
        List<Fused<T>> fused = new ArrayList<>(ordered.size());
        for (Map.Entry<String, Double> entry : ordered) {
            fused.add(new Fused<>(firstSeen.get(entry.getKey()), entry.getValue()));
        }
        return fused;
    }

    public record Fused<T>(T item, double score) {
    }
}
