package com.baiflow.download.dto.response;

import com.baiflow.download.entity.DownloadTask;
import com.baiflow.download.enums.DownloadTaskStatus;

import java.time.LocalDateTime;

/**
 * 下载任务响应 DTO — 不暴露内部 aria2 GID，前端只需 ID、URL、状态、进度等。
 */
public record DownloadTaskInfo(
        String id,
        String sourceUrl,
        String targetStorageRootId,
        String targetRelativePath,
        String fileName,
        DownloadTaskStatus status,
        Integer progress,
        Long totalBytes,
        Long completedBytes,
        Long speedBytesPerSecond,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
    public static DownloadTaskInfo from(DownloadTask t) {
        return new DownloadTaskInfo(
                t.getId(), t.getSourceUrl(), t.getTargetStorageRootId(),
                t.getTargetRelativePath(), t.getFileName(), t.getStatus(),
                t.getProgress(), t.getTotalBytes(), t.getCompletedBytes(),
                t.getSpeedBytesPerSecond(), t.getErrorMessage(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt()
        );
    }
}
