package com.study.rag.retrieve;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ParentExpander {

    private ParentExpander() {
    }

    public static List<RankedChunk> expand(List<RankedChunk> ranked) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        List<RankedChunk> expanded = new ArrayList<>();
        Set<String> parents = new LinkedHashSet<>();
        for (RankedChunk chunk : ranked) {
            if (chunk.parentContent() == null || chunk.parentContent().isBlank()) {
                expanded.add(chunk);
                continue;
            }
            String parent = chunk.parentContent().trim();
            if (!parents.add(parent)) {
                continue;
            }
            expanded.add(parent.equals(chunk.content()) ? chunk : chunk.withContent(parent));
        }
        return expanded;
    }
}
