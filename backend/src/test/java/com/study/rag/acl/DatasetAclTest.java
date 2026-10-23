package com.study.rag.acl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetAclTest {

    private final WorkspaceUser owner = new WorkspaceUser("owner", "所有者", "owner");
    private final WorkspaceUser member = new WorkspaceUser("member", "普通成员", "member");
    private final WorkspaceUser other = new WorkspaceUser("other", "其他人", "member");

    private final KnowledgeDataset onlyMe = new KnowledgeDataset("only", "私人库", DatasetAcl.ONLY_ME, "owner", List.of());
    private final KnowledgeDataset partial = new KnowledgeDataset("part", "部分成员", DatasetAcl.PARTIAL_MEMBERS, "owner", List.of("member"));
    private final KnowledgeDataset team = new KnowledgeDataset("team", "全员库", DatasetAcl.ALL_TEAM_MEMBERS, "owner", List.of());
    private final List<KnowledgeDataset> datasets = List.of(onlyMe, partial, team);

    @Test
    void memberCannotSeeAnotherUsersPrivateDataset() {
        assertFalse(DatasetAcl.canRead(member, onlyMe));
        assertFalse(DatasetAcl.visibleIds(member, datasets).contains("only"));
    }

    @Test
    void memberCannotSeePartialDatasetUnlessListed() {
        assertTrue(DatasetAcl.canRead(member, partial));
        assertFalse(DatasetAcl.canRead(other, partial));
        assertFalse(DatasetAcl.visibleIds(other, datasets).contains("part"));
    }

    @Test
    void memberCanSeeTeamDatasetAndOwnerCanSeeAll() {
        assertTrue(DatasetAcl.visibleIds(member, datasets).contains("team"));
        assertEquals(List.of("only", "part", "team"), DatasetAcl.visibleIds(owner, datasets));
    }

    @Test
    void emptyVisibleIdsSkipTheSearchInsteadOfFilteringHitsLater() {
        PreparedSearch skipped = PreparedSearch.prepare(List.of());
        PreparedSearch ready = PreparedSearch.prepare(DatasetAcl.visibleIds(member, List.of(team)));

        assertFalse(skipped.execute());
        assertTrue(ready.execute());
        assertEquals(List.of("team"), ready.datasetIds());
    }
}
