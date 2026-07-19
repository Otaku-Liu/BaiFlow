package com.baiflow.file.dto.response;

import com.baiflow.file.entity.FileItem;
import com.baiflow.file.enums.FileItemStatus;
import com.baiflow.file.enums.ItemType;
import com.baiflow.file.enums.PrivacyMode;

import java.time.LocalDateTime;

public record FileItemInfo(String id, String storageRootId, String parentId, String ownerUserId,
                           String name, String relativePath, ItemType itemType, Long sizeBytes,
                           String mimeType, String hashSha256, PrivacyMode privacyMode,
                           FileItemStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static FileItemInfo from(FileItem f) {
        return new FileItemInfo(f.getId(), f.getStorageRootId(), f.getParentId(), f.getOwnerUserId(),
                f.getName(), f.getRelativePath(), f.getItemType(), f.getSizeBytes(),
                f.getMimeType(), f.getHashSha256(), f.getPrivacyMode(), f.getStatus(),
                f.getCreatedAt(), f.getUpdatedAt());
    }
}
