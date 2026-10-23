package com.study.rag.acl;

import java.util.List;

public record WorkspaceUser(String id, String name, String role) {

    public boolean owner() {
        return "owner".equals(role);
    }
}
