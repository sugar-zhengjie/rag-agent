package com.study.rag.api;

import com.study.rag.acl.DatasetCatalog;
import com.study.rag.acl.KnowledgeDataset;
import com.study.rag.acl.WorkspaceUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DatasetController {

    private final DatasetCatalog catalog;

    public DatasetController(DatasetCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/users")
    public List<WorkspaceUser> users() {
        return catalog.users();
    }

    @GetMapping("/api/datasets")
    public List<DatasetView> datasets(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        WorkspaceUser user = catalog.user(userId);
        return catalog.visible(user).stream().map(dataset -> DatasetView.from(dataset, user)).toList();
    }

    @PostMapping("/api/datasets")
    public DatasetView create(@RequestHeader(value = "X-User-Id", required = false) String userId,
                              @RequestBody DatasetRequest request) {
        WorkspaceUser user = catalog.user(userId);
        return DatasetView.from(catalog.create(user, request.name(), request.permission(), request.memberIds()), user);
    }

    @PatchMapping("/api/datasets/{datasetId}")
    public DatasetView update(@RequestHeader(value = "X-User-Id", required = false) String userId,
                              @PathVariable String datasetId,
                              @RequestBody DatasetRequest request) {
        WorkspaceUser user = catalog.user(userId);
        return DatasetView.from(catalog.update(user, datasetId, request.permission(), request.memberIds()), user);
    }

    public record DatasetRequest(String name, String permission, List<String> memberIds) {
    }

    public record DatasetView(
            String id,
            String name,
            String permission,
            String createdBy,
            List<String> memberIds,
            boolean manageable) {

        static DatasetView from(KnowledgeDataset dataset, WorkspaceUser user) {
            return new DatasetView(
                    dataset.id(),
                    dataset.name(),
                    dataset.permission(),
                    dataset.createdBy(),
                    dataset.memberIds(),
                    DatasetCatalog.canManage(user, dataset));
        }
    }
}
