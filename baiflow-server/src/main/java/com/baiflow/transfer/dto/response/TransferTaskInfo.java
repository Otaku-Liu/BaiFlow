package com.baiflow.transfer.dto.response;

import com.baiflow.transfer.entity.TransferTask;
import com.baiflow.transfer.enums.TransferTaskStatus;
import com.baiflow.transfer.enums.TransferTaskType;

import java.time.LocalDateTime;

/**
 * 传输任务响应 DTO — 不暴露内部 ID 细节，仅返回前端需要的信息。
 */
public record TransferTaskInfo(
        String id,
        String createdBy,
        TransferTaskType taskType,
        String sourceType,
        String targetType,
        TransferTaskStatus status,
        Integer progress,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TransferTaskInfo from(TransferTask t) {
        return new TransferTaskInfo(t.getId(), t.getCreatedBy(), t.getTaskType(),
                t.getSourceType(), t.getTargetType(), t.getStatus(),
                t.getProgress(), t.getErrorMessage(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
