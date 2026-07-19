-- Phase 5: Transfer Center & Notifications — transfer_task, notification

CREATE TABLE IF NOT EXISTS transfer_task (
    id               VARCHAR(32)  NOT NULL COMMENT '主键，UUID 自动生成',
    created_by       VARCHAR(32)  NOT NULL COMMENT '创建者用户 ID',
    task_type        VARCHAR(16)  NOT NULL DEFAULT 'UPLOAD' COMMENT '任务类型：UPLOAD / DOWNLOAD / DEVICE_SEND',
    source_type      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '来源类型描述',
    target_type      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '目标类型描述',
    status           VARCHAR(16)  NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING / RUNNING / PAUSED / FAILED / COMPLETED',
    progress         INT          NOT NULL DEFAULT 0 COMMENT '进度百分比（0-100）',
    error_message    VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '失败时的错误描述',
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tt_user_status (created_by, status, created_at),
    KEY idx_tt_type_status (task_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='传输任务（上传、下载、设备流转）';

CREATE TABLE IF NOT EXISTS notification (
    id               VARCHAR(32)  NOT NULL COMMENT '主键，UUID 自动生成',
    user_id          VARCHAR(32)  NOT NULL COMMENT '目标用户 ID',
    level            VARCHAR(16)  NOT NULL DEFAULT 'INFO' COMMENT '通知级别：INFO / WARN / ERROR',
    title            VARCHAR(255) NOT NULL DEFAULT '' COMMENT '通知标题',
    content          VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '通知正文',
    read_status      VARCHAR(16)  NOT NULL DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD（未读）/ READ（已读）',
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    read_at          TIMESTAMP NULL COMMENT '标记已读的时间',
    PRIMARY KEY (id),
    KEY idx_notif_user_read (user_id, read_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知';
