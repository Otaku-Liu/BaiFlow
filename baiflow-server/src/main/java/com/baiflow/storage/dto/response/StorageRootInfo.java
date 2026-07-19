package com.baiflow.storage.dto.response;

import com.baiflow.storage.entity.StorageRoot;
import com.baiflow.storage.enums.StorageRootStatus;
import com.baiflow.storage.enums.StorageRootType;

import java.time.LocalDateTime;

public record StorageRootInfo(String id, String name, StorageRootType type, String rootPath,
                              StorageRootStatus status, Boolean readonly, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static StorageRootInfo from(StorageRoot r) {
        return new StorageRootInfo(r.getId(), r.getName(), r.getType(), r.getRootPath(), r.getStatus(), r.getReadonly(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
