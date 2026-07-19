package com.baiflow.transfer.entity;

import com.baiflow.transfer.enums.TransferTaskStatus;
import com.baiflow.transfer.enums.TransferTaskType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传输任务实体 — 统一记录上传、下载、设备流转任务。
 */
@Data
@TableName("transfer_task")
public class TransferTask {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建者用户 ID */
    private String createdBy;

    /** 任务类型：UPLOAD / DOWNLOAD / DEVICE_SEND */
    private TransferTaskType taskType;

    /** 来源类型描述 */
    private String sourceType;

    /** 目标类型描述 */
    private String targetType;

    /** 状态：WAITING / RUNNING / PAUSED / FAILED / COMPLETED */
    private TransferTaskStatus status;

    /** 进度百分比（0-100） */
    private Integer progress;

    /** 失败时的错误描述 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
