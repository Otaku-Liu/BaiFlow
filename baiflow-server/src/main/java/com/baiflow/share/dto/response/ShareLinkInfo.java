package com.baiflow.share.dto.response;

import com.baiflow.share.entity.BfShareLink;
import com.baiflow.share.enums.ShareStatus;
import java.time.LocalDateTime;

/** 分享链接响应 DTO（不含 hash 字段）*/
public record ShareLinkInfo(
        String id, String targetFileItemId, String shareType, String accessMode,
        String expiresAt, int maxViews, int viewCount, int maxDownloads, int downloadCount,
        boolean requirePrivatePassword, String status, String createdAt,
        String token, String ownerUsername, String ownerDisplayName
) {
    public static ShareLinkInfo from(BfShareLink s) {
        return new ShareLinkInfo(s.getId(), s.getTargetFileItemId(),
                s.getShareType().name(), s.getAccessMode().name(),
                s.getExpiresAt() != null ? s.getExpiresAt().toString() : null,
                s.getMaxViews(), s.getViewCount(), s.getMaxDownloads(), s.getDownloadCount(),
                s.getRequirePrivatePassword(), s.getStatus().name(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                null, s.getOwnerUsername(), s.getOwnerDisplayName());
    }

    public static ShareLinkInfo from(BfShareLink s, String token) {
        return new ShareLinkInfo(s.getId(), s.getTargetFileItemId(),
                s.getShareType().name(), s.getAccessMode().name(),
                s.getExpiresAt() != null ? s.getExpiresAt().toString() : null,
                s.getMaxViews(), s.getViewCount(), s.getMaxDownloads(), s.getDownloadCount(),
                s.getRequirePrivatePassword(), s.getStatus().name(),
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : null,
                token, s.getOwnerUsername(), s.getOwnerDisplayName());
    }
}
