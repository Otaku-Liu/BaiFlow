package com.baiflow.uploadrecord.dto.response;

import com.baiflow.uploadrecord.entity.BfUploadRecord;
import com.baiflow.uploadrecord.enums.UploadSource;

import java.time.LocalDateTime;

/**
 * 上传记录响应 DTO — 附上传人用户名（供 admin 视图区分不同用户）。
 */
public record UploadRecordInfo(String id, String fileId, String fileName, String uploaderUserId,
                               String uploaderUsername, UploadSource source, String ipAddress,
                               String userAgent, LocalDateTime createdAt) {

    public static UploadRecordInfo from(BfUploadRecord r, String uploaderUsername) {
        return new UploadRecordInfo(r.getId(), r.getFileId(), r.getFileName(), r.getUploaderUserId(),
                uploaderUsername, r.getSource(), r.getIpAddress(), r.getUserAgent(), r.getCreatedAt());
    }
}
