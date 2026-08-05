-- ============================================================
-- BaiFlow 播放/阅读进度 + 随手记 迁移脚本（版本 2）
-- 说明：由原 V2__playback_progress.sql 与原 V3__quick_notes.sql 合并而来，
--       统一管理跨设备断点续看与随手记笔记相关的三张表。所有表/字段均带注释。
-- 可重复执行（使用 IF NOT EXISTS）
-- ============================================================

-- -----------------------------------------------------------
-- 1. 播放/阅读进度表（视频/音频/PDF/文本，跨设备断点续看）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_playback_progress` (
    `id`             VARCHAR(32) NOT NULL COMMENT '主键，UUID',
    `user_id`        VARCHAR(32) NOT NULL COMMENT '进度所属用户 ID',
    `file_item_id`   VARCHAR(32) NOT NULL COMMENT '对应文件 ID（bf_file_item.id）',
    `position_type`  VARCHAR(16) NOT NULL DEFAULT 'SECONDS' COMMENT '进度类型：SECONDS（视频/音频秒数）/ PAGE（PDF 页码）/ SCROLL_PERCENT（文本滚动百分比）',
    `position_value` DOUBLE      NOT NULL DEFAULT 0 COMMENT '进度值：秒数 / 页码 / 0~1 滚动百分比',
    `created_at`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_file` (`user_id`, `file_item_id`),
    KEY `idx_user` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='播放/阅读进度表（每个用户对每个文件一条记录，支持跨设备断点续看）';

-- -----------------------------------------------------------
-- 2. 随手记笔记表（便签/笔记，正文存 Markdown）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_note` (
    `id`         VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `user_id`    VARCHAR(32)  NOT NULL COMMENT '笔记所有者用户 ID',
    `title`      VARCHAR(200) NOT NULL DEFAULT '' COMMENT '笔记标题',
    `content`    LONGTEXT     NOT NULL COMMENT 'Markdown 正文（直接落库，非文件系统）',
    `status`     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE（正常）/ DELETED（软删除）',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（后写覆盖同步的时间基准）',
    `deleted_at` TIMESTAMP    NULL COMMENT '软删除时间（NULL 表示未删除）',
    PRIMARY KEY (`id`),
    KEY `idx_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='随手记笔记表（标题 + Markdown 正文，独立于文件系统存储）';

-- -----------------------------------------------------------
-- 3. 笔记阅读进度表（跨设备续读长笔记）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_note_progress` (
    `id`             VARCHAR(32) NOT NULL COMMENT '主键，UUID',
    `user_id`        VARCHAR(32) NOT NULL COMMENT '进度所属用户 ID',
    `note_id`        VARCHAR(32) NOT NULL COMMENT '对应笔记 ID（bf_note.id）',
    `position_type`  VARCHAR(16) NOT NULL DEFAULT 'SCROLL_PERCENT' COMMENT '进度类型（当前固定为 SCROLL_PERCENT）',
    `position_value` DOUBLE      NOT NULL DEFAULT 0 COMMENT '滚动百分比（0~1）',
    `created_at`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_note` (`user_id`, `note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记阅读进度表（每个用户对每篇笔记一条记录，支持跨设备续读）';
