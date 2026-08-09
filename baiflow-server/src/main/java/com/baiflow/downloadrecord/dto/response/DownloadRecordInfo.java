package com.baiflow.downloadrecord.dto.response;

import com.baiflow.downloadrecord.entity.DownloadRecord;
import com.baiflow.downloadrecord.enums.DownloadSource;

import java.time.LocalDateTime;

/**
 * 下载记录响应 DTO — 附下载人用户名（分享匿名下载为空）。
 */
public record DownloadRecordInfo(String id, String fileId, String fileName, String downloaderUserId,
                                 String downloaderUsername, DownloadSource source, String shareId,
                                 String ipAddress, String userAgent, LocalDateTime createdAt) {

    public static DownloadRecordInfo from(DownloadRecord r, String downloaderUsername) {
        return new DownloadRecordInfo(r.getId(), r.getFileId(), r.getFileName(), r.getDownloaderUserId(),
                downloaderUsername, r.getSource(), r.getShareId(), r.getIpAddress(), r.getUserAgent(),
                r.getCreatedAt());
    }
}
