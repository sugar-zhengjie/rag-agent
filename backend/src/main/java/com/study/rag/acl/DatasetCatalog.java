package com.study.rag.acl;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DatasetCatalog {

    public static final String OWNER_ID = "owner";
    public static final String MEMBER_ID = "member";

    private static final WorkspaceUser OWNER = new WorkspaceUser(OWNER_ID, "所有者", "owner");
    private static final WorkspaceUser MEMBER = new WorkspaceUser(MEMBER_ID, "普通成员", "member");

    private final ConcurrentHashMap<String, KnowledgeDataset> datasets = new ConcurrentHashMap<>();

    public List<WorkspaceUser> users() {
        return List.of(OWNER, MEMBER);
    }

    public WorkspaceUser user(String userId) {
        if (userId == null || userId.isBlank() || OWNER_ID.equals(userId)) {
            return OWNER;
        }
        if (MEMBER_ID.equals(userId)) {
            return MEMBER;
        }
        throw new IllegalArgumentException("用户不存在");
    }

    public List<KnowledgeDataset> visible(WorkspaceUser user) {
        return datasets.values().stream().filter(dataset -> DatasetAcl.canRead(user, dataset)).toList();
    }

    public List<String> visibleIds(String userId) {
        return DatasetAcl.visibleIds(user(userId), datasets.values());
    }

    public boolean canRead(WorkspaceUser user, String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            return false;
        }
        return DatasetAcl.canRead(user, datasets.get(datasetId));
    }

    public KnowledgeDataset create(WorkspaceUser user, String name, String permission, List<String> memberIds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        String normalized = DatasetAcl.normalizePermission(permission);
        KnowledgeDataset dataset = new KnowledgeDataset(
                UUID.randomUUID().toString(),
                name.trim(),
                normalized,
                user.id(),
                members(normalized, memberIds));
        datasets.put(dataset.id(), dataset);
        return dataset;
    }

    public KnowledgeDataset update(WorkspaceUser user, String datasetId, String permission, List<String> memberIds) {
        KnowledgeDataset current = datasets.get(datasetId);
        if (current == null || !canManage(user, current)) {
            throw new IllegalArgumentException("知识库不存在或没有管理权限");
        }
        String normalized = DatasetAcl.normalizePermission(permission);
        KnowledgeDataset updated = new KnowledgeDataset(
                current.id(),
                current.name(),
                normalized,
                current.createdBy(),
                members(normalized, memberIds));
        datasets.put(datasetId, updated);
        return updated;
    }

    public static boolean canManage(WorkspaceUser user, KnowledgeDataset dataset) {
        return user != null && dataset != null && (user.owner() || user.id().equals(dataset.createdBy()));
    }

    private static List<String> members(String permission, List<String> memberIds) {
        if (!DatasetAcl.PARTIAL_MEMBERS.equals(permission) || memberIds == null) {
            return List.of();
        }
        return List.copyOf(memberIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList());
    }

    public List<KnowledgeDataset> all() {
        return new ArrayList<>(datasets.values());
    }
}
