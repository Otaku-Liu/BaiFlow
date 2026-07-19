package com.baiflow.download.entity;

import com.baiflow.download.enums.DownloadTaskStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下载任务实体 — 代表一个由 aria2 执行的下载任务。
 * <p>
 * 任务由用户创建后通过 aria2 JSON-RPC 提交给下载引擎，
 * 后端定时同步 aria2 状态并更新此表的进度、速度和状态字段。
 * 下载完成后自动在 {@code file_item} 表中创建对应的文件记录。
 */
@Data
@TableName("download_task")
public class DownloadTask {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建者用户 ID */
    private String createdBy;

    /** 下载源 URL */
    private String sourceUrl;

    /** aria2 返回的任务 GID，用于后续状态查询和操作 */
    private String aria2Gid;

    /** 下载目标存储根目录 ID */
    private String targetStorageRootId;

    /** 下载完成后文件所在相对路径（相对于存储根目录） */
    private String targetRelativePath;

    /** 下载文件名（由 aria2 返回或从 URL 推断） */
    private String fileName;

    /** 下载状态 */
    private DownloadTaskStatus status;

    /** 下载进度（0-100） */
    private Integer progress;

    /** 文件总大小（字节），下载完成后由 aria2 返回 */
    private Long totalBytes;

    /** 已下载字节数 */
    private Long completedBytes;

    /** 下载速度（字节/秒） */
    private Long speedBytesPerSecond;

    /** 失败时的错误描述 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 下载完成时间 */
    private LocalDateTime completedAt;
}
