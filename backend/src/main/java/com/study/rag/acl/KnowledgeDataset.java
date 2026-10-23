package com.study.rag.acl;

import java.util.List;

public record KnowledgeDataset(
        String id,
        String name,
        String permission,
        String createdBy,
        List<String> memberIds) {
}
