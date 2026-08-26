package com.baiflow.file.dto.response;

import com.baiflow.file.entity.FileItem;
import com.baiflow.file.enums.FileItemStatus;
import com.baiflow.file.enums.ItemType;
import com.baiflow.file.enums.PrivacyMode;

import java.time.LocalDateTime;

public record FileItemInfo(String id, String storageRootId, String parentId, String ownerUserId,
                           String name, String relativePath, ItemType itemType, Long sizeBytes,
                           String mimeType, String hashSha256, PrivacyMode privacyMode,
                           FileItemStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
                           LocalDateTime lastOpenedAt, int downloadCount, Long childCount) {
    public static FileItemInfo from(FileItem f) {
        return from(f, 0, null);
    }

    /** 附下载次数与直接子项数构建（文件列表批量统计时使用；隐私目录 childCount 为 null） */
    public static FileItemInfo from(FileItem f, int downloadCount, Long childCount) {
        return new FileItemInfo(f.getId(), f.getStorageRootId(), f.getParentId(), f.getOwnerUserId(),
                f.getName(), f.getRelativePath(), f.getItemType(), f.getSizeBytes(),
                f.getMimeType(), f.getHashSha256(), f.getPrivacyMode(), f.getStatus(),
                f.getCreatedAt(), f.getUpdatedAt(), f.getLastOpenedAt(), downloadCount, childCount);
    }
}
