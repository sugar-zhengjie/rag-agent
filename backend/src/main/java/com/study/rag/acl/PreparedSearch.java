package com.study.rag.acl;

import java.util.List;

public record PreparedSearch(boolean execute, List<String> datasetIds) {

    public static PreparedSearch prepare(List<String> datasetIds) {
        if (datasetIds == null || datasetIds.isEmpty()) {
            return new PreparedSearch(false, List.of());
        }
        return new PreparedSearch(true, List.copyOf(datasetIds));
    }
}
