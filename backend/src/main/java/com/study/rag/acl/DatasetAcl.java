package com.study.rag.acl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DatasetAcl {

    public static final String ALL_TEAM_MEMBERS = "all_team_members";
    public static final String ONLY_ME = "only_me";
    public static final String PARTIAL_MEMBERS = "partial_members";

    private DatasetAcl() {
    }

    public static List<String> visibleIds(WorkspaceUser user, Collection<KnowledgeDataset> datasets) {
        if (user == null || datasets == null || datasets.isEmpty()) {
            return List.of();
        }
        if (user.owner()) {
            return datasets.stream().map(KnowledgeDataset::id).toList();
        }
        List<String> ids = new ArrayList<>();
        for (KnowledgeDataset dataset : datasets) {
            if (canRead(user, dataset)) {
                ids.add(dataset.id());
            }
        }
        return List.copyOf(ids);
    }

    public static boolean canRead(WorkspaceUser user, KnowledgeDataset dataset) {
        if (user == null || dataset == null) {
            return false;
        }
        if (user.owner()) {
            return true;
        }
        return switch (dataset.permission()) {
            case ALL_TEAM_MEMBERS -> true;
            case ONLY_ME -> user.id().equals(dataset.createdBy());
            case PARTIAL_MEMBERS -> user.id().equals(dataset.createdBy())
                    || (dataset.memberIds() != null && dataset.memberIds().contains(user.id()));
            default -> false;
        };
    }

    public static String normalizePermission(String permission) {
        if (ALL_TEAM_MEMBERS.equals(permission) || ONLY_ME.equals(permission) || PARTIAL_MEMBERS.equals(permission)) {
            return permission;
        }
        throw new IllegalArgumentException("不支持的知识库权限");
    }
}
