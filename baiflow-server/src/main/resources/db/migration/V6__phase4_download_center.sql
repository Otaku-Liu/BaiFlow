-- Phase 4: Download Center MVP — download_task 表，aria2 下载任务管理

CREATE TABLE IF NOT EXISTS download_task (
    id                       VARCHAR(32)  NOT NULL COMMENT '主键，UUID 自动生成',
    created_by               VARCHAR(32)  NOT NULL COMMENT '创建者用户 ID',
    source_url               VARCHAR(2048) NOT NULL COMMENT '下载源 URL',
    aria2_gid                VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'aria2 任务 GID（用于状态同步）',
    target_storage_root_id   VARCHAR(32)  NOT NULL COMMENT '目标存储根目录 ID',
    target_relative_path     VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '下载完成后文件所在相对路径',
    file_name                VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '下载文件名（由 aria2 返回或 URL 推断）',
    status                   VARCHAR(16)  NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING / RUNNING / PAUSED / FAILED / COMPLETED / DELETED',
    progress                 INT          NOT NULL DEFAULT 0 COMMENT '下载进度（0-100）',
    total_bytes              BIGINT       NOT NULL DEFAULT 0 COMMENT '文件总大小（字节）',
    completed_bytes          BIGINT       NOT NULL DEFAULT 0 COMMENT '已下载字节数',
    speed_bytes_per_second   BIGINT       NOT NULL DEFAULT 0 COMMENT '下载速度（字节/秒）',
    error_message            VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '失败时的错误描述',
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at             TIMESTAMP NULL COMMENT '下载完成时间',
    PRIMARY KEY (id),
    KEY idx_dt_user_status (created_by, status, created_at),
    KEY idx_dt_aria2_gid (aria2_gid),
    KEY idx_dt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下载任务（aria2 下载管理）';
