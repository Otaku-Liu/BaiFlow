-- ============================================================
-- R__V1 统一 schema（可重复迁移，唯一脚本）：全部表结构（含完整字段和表注释）+ 初始数据
-- 所有表 IF NOT EXISTS、管理员 INSERT WHERE NOT EXISTS，幂等；文件有改动即自动重新执行
-- 新表 DDL 一律追加于本文件末尾
-- ============================================================

-- -----------------------------------------------------------
-- 1. 系统用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_user` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '用户主键，UUID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录用户名，全局唯一',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希后的密码，绝不存明文',
    `display_name`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '显示名称',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER / GUEST',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL（正常）/ DISABLED（禁用）/ LOCKED（锁定）',
    `avatar_url`    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像访问 URL（nginx 静态文件链接）',
    `last_login_at` TIMESTAMP    NULL COMMENT '最后登录时间',
    `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 默认管理员账号（用户名 admin，密码 admin）
INSERT INTO `bf_user` (`id`, `username`, `password_hash`, `display_name`, `role`, `status`, `avatar_url`, `created_at`, `updated_at`)
SELECT REPLACE(UUID(), '-', ''), 'admin',
       '$2a$10$J56W4KahX.odv.j2jNdzie00DVgxql0Lo4Fc3P6LUTz9iwIdEexQW',
       'Administrator', 'ADMIN', 'NORMAL', '', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `bf_user` WHERE `username` = 'admin');

-- -----------------------------------------------------------
-- 2. 存储根目录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_storage_root` (
    `id`         VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `name`       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '存储根目录的显示名称',
    `type`       VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '类型：LOCAL（本地磁盘）/ NAS_MOUNT（NAS 挂载）',
    `root_path`  VARCHAR(512) NOT NULL COMMENT '磁盘上的绝对路径，作为所有文件操作的安全锚点',
    `status`     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE（可用）/ OFFLINE（离线）/ DISABLED（禁用）',
    `readonly`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否只读（1 表示禁止写入、删除、移动操作）',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_storage_root_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存储根目录表（定义文件操作的安全边界）';

-- -----------------------------------------------------------
-- 3. 文件项表（文件和目录元数据）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_file_item` (
    `id`                    VARCHAR(32)   NOT NULL COMMENT '主键，UUID',
    `storage_root_id`       VARCHAR(32)   NOT NULL COMMENT '所属存储根目录 ID',
    `parent_id`             VARCHAR(32)   NULL COMMENT '父目录 ID（NULL 表示该存储根目录的根层级）',
    `owner_user_id`         VARCHAR(32)   NOT NULL COMMENT '文件/目录的所有者用户 ID',
    `name`                  VARCHAR(255)  NOT NULL COMMENT '文件/目录名称（不含路径）',
    `relative_path`         VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '相对于存储根目录的路径',
    `item_type`             VARCHAR(16)   NOT NULL DEFAULT 'FILE' COMMENT '类型：FILE（文件）/ DIRECTORY（目录）',
    `size_bytes`            BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小（字节），目录为 0',
    `mime_type`             VARCHAR(128)  NOT NULL DEFAULT '' COMMENT 'MIME 类型（目录为空字符串）',
    `hash_sha256`           VARCHAR(128)  NOT NULL DEFAULT '' COMMENT 'SHA-256 哈希值（目录为空字符串）',
    `privacy_mode`          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '隐私模式：NORMAL（正常可见）/ PRIVATE（需额外密码）',
    `privacy_password_hash` VARCHAR(255)  NOT NULL DEFAULT '' COMMENT 'BCrypt 哈希后的隐私访问密码（仅 PRIVATE 模式目录有值）',
    `status`                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE（正常）/ DELETED（已软删除）',
    `created_at`            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`            TIMESTAMP     NULL COMMENT '软删除时间（NULL 表示未删除）',
    PRIMARY KEY (`id`),
    KEY `idx_file_item_storage_parent` (`storage_root_id`, `parent_id`),
    KEY `idx_file_item_storage_path` (`storage_root_id`, `relative_path`(255)),
    KEY `idx_file_item_owner` (`owner_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件项表（存储文件和目录的元数据，文件本体落磁盘）';

-- -----------------------------------------------------------
-- 4. 用户存储权限表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_user_storage_permission` (
    `id`              VARCHAR(32) NOT NULL COMMENT '主键，UUID',
    `user_id`         VARCHAR(32) NOT NULL COMMENT '被授权的用户 ID',
    `storage_root_id` VARCHAR(32) NOT NULL COMMENT '授权的存储根目录 ID',
    `file_item_id`    VARCHAR(32) NULL COMMENT '授权的具体文件或目录 ID（NULL 表示整个存储根目录）',
    `permission`      VARCHAR(16) NOT NULL DEFAULT 'READ' COMMENT '权限级别：READ（只读）/ WRITE（读写）/ MANAGE（管理）',
    `created_by`      VARCHAR(32) NOT NULL COMMENT '授权创建者用户 ID',
    `created_at`      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_usp_user_root` (`user_id`, `storage_root_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户存储权限表（定义用户对存储根目录或文件/文件夹的访问级别）';

-- -----------------------------------------------------------
-- 5. 隐私文件夹访问会话表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_private_folder_access` (
    `id`                VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `user_id`           VARCHAR(32)  NOT NULL COMMENT '访问用户 ID',
    `file_item_id`      VARCHAR(32)  NOT NULL COMMENT '隐私文件夹 ID',
    `access_token_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希后的短期访问令牌',
    `expires_at`        TIMESTAMP    NOT NULL COMMENT '会话过期时间（建议 30 分钟有效）',
    `created_at`        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_pfa_user_folder` (`user_id`, `file_item_id`),
    KEY `idx_pfa_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='隐私文件夹访问会话表（密码验证通过后的短期会话）';

-- -----------------------------------------------------------
-- 6. 传输任务表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_transfer_task` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '主键，UUID',
    `created_by`    VARCHAR(32)   NOT NULL COMMENT '创建者用户 ID',
    `task_type`     VARCHAR(16)   NOT NULL DEFAULT 'UPLOAD' COMMENT '任务类型：UPLOAD / DOWNLOAD / DEVICE_SEND',
    `source_type`   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '来源类型描述',
    `target_type`   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '目标类型描述',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING / RUNNING / PAUSED / FAILED / COMPLETED',
    `progress`      INT           NOT NULL DEFAULT 0 COMMENT '进度百分比（0-100）',
    `error_message` VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '失败时的错误描述',
    `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tt_user_status` (`created_by`, `status`, `created_at`),
    KEY `idx_tt_type_status` (`task_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='传输任务表（上传、下载、设备流转）';

-- -----------------------------------------------------------
-- 7. 用户通知表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_notification` (
    `id`          VARCHAR(32)   NOT NULL COMMENT '主键，UUID',
    `user_id`     VARCHAR(32)   NOT NULL COMMENT '目标用户 ID',
    `level`       VARCHAR(16)   NOT NULL DEFAULT 'INFO' COMMENT '通知级别：INFO / WARN / ERROR',
    `title`       VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '通知标题',
    `content`     VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '通知正文',
    `read_status` VARCHAR(16)   NOT NULL DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD（未读）/ READ（已读）',
    `created_at`  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `read_at`     TIMESTAMP     NULL COMMENT '标记已读的时间',
    PRIMARY KEY (`id`),
    KEY `idx_notif_user_read` (`user_id`, `read_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

-- -----------------------------------------------------------
-- 8. 下载任务表（aria2 下载管理）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_download_task` (
    `id`                     VARCHAR(32)   NOT NULL COMMENT '主键，UUID',
    `created_by`             VARCHAR(32)   NOT NULL COMMENT '创建者用户 ID',
    `owner_username`         VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建者用户名快照（用户删除后保留）',
    `owner_display_name`     VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '创建者展示名快照（用户删除后保留）',
    `source_url`             VARCHAR(2048) NOT NULL COMMENT '下载源 URL',
    `aria2_gid`              VARCHAR(64)   NOT NULL DEFAULT '' COMMENT 'aria2 返回的任务 GID，用于状态查询和操作',
    `target_storage_root_id` VARCHAR(32)   NOT NULL COMMENT '目标存储根目录 ID',
    `target_relative_path`   VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '下载完成后文件所在相对路径（相对于存储根目录）',
    `file_name`              VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '下载文件名（由 aria2 返回或从 URL 推断）',
    `status`                 VARCHAR(16)   NOT NULL DEFAULT 'WAITING' COMMENT '状态：WAITING / RUNNING / PAUSED / FAILED / COMPLETED / DELETED',
    `progress`               INT           NOT NULL DEFAULT 0 COMMENT '下载进度（0-100）',
    `total_bytes`            BIGINT        NOT NULL DEFAULT 0 COMMENT '文件总大小（字节）',
    `completed_bytes`        BIGINT        NOT NULL DEFAULT 0 COMMENT '已下载字节数',
    `speed_bytes_per_second` BIGINT        NOT NULL DEFAULT 0 COMMENT '下载速度（字节/秒）',
    `error_message`          VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '失败时的错误描述',
    `created_at`             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `completed_at`           TIMESTAMP     NULL COMMENT '下载完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_dt_user_status` (`created_by`, `status`, `created_at`),
    KEY `idx_dt_aria2_gid` (`aria2_gid`),
    KEY `idx_dt_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下载任务表（aria2 下载管理）';

-- -----------------------------------------------------------
-- 9. 分享链接表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_share_link` (
    `id`                       VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `target_file_item_id`      VARCHAR(32)  NOT NULL COMMENT '被分享的文件或文件夹 ID',
    `created_by`               VARCHAR(32)  NOT NULL COMMENT '创建者用户 ID',
    `owner_username`           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者用户名快照（用户删除后保留）',
    `owner_display_name`       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建者展示名快照（用户删除后保留）',
    `token_hash`               VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希后的分享 token',
    `extraction_code_hash`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'BCrypt 哈希后的提取码（空字符串表示未设置提取码）',
    `share_type`               VARCHAR(16)  NOT NULL DEFAULT 'FILE' COMMENT '分享类型：FILE / FOLDER',
    `access_mode`              VARCHAR(16)  NOT NULL DEFAULT 'VIEW' COMMENT '访问模式：VIEW（浏览）/ DOWNLOAD（可下载）',
    `expires_at`               TIMESTAMP    NULL COMMENT '过期时间（NULL 表示永不过期）',
    `max_views`                INT          NOT NULL DEFAULT 0 COMMENT '最大访问次数（0 表示不限制）',
    `view_count`               INT          NOT NULL DEFAULT 0 COMMENT '已访问次数',
    `max_downloads`            INT          NOT NULL DEFAULT 0 COMMENT '最大下载次数（0 表示不限制）',
    `download_count`           INT          NOT NULL DEFAULT 0 COMMENT '已下载次数',
    `require_private_password` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否需要隐私文件夹密码（分享目标是隐私文件夹时为 1）',
    `status`                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / EXPIRED / REVOKED',
    `created_at`               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_sl_token_hash` (`token_hash`(64)),
    KEY `idx_sl_created_by` (`created_by`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享链接表';

-- -----------------------------------------------------------
-- 10. 分享访问日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_share_access_log` (
    `id`             VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `share_link_id`  VARCHAR(32)  NOT NULL COMMENT '分享链接 ID',
    `action`         VARCHAR(32)  NOT NULL DEFAULT 'VIEW' COMMENT '操作类型：VIEW / DOWNLOAD / VERIFY_CODE / FAILED',
    `ip_address`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '访问者 IP 地址',
    `user_agent`     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '访问者 User-Agent',
    `success`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功（1=成功，0=失败）',
    `failure_reason` VARCHAR(256) NOT NULL DEFAULT '' COMMENT '失败原因（success=0 时填写）',
    `created_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_sal_share_link` (`share_link_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享访问日志表';

-- -----------------------------------------------------------
-- 11. 操作审计日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_audit_log` (
    `id`            VARCHAR(32)   NOT NULL COMMENT '主键，UUID',
    `actor_user_id` VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '操作者用户 ID（匿名操作为空字符串）',
    `action`        VARCHAR(64)   NOT NULL COMMENT '操作类型：LOGIN_SUCCESS / LOGIN_FAILED / FILE_DELETE / SHARE_CREATE / SHARE_ACCESS / SHARE_REVOKE 等',
    `target_type`   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '操作目标类型：USER / FILE / SHARE_LINK 等',
    `target_id`     VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '操作目标 ID',
    `ip_address`    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '操作者 IP 地址',
    `user_agent`    VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '操作者 User-Agent',
    `detail`        VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '操作详情（补充描述）',
    `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_al_actor` (`actor_user_id`, `created_at`),
    KEY `idx_al_action` (`action`, `created_at`),
    KEY `idx_al_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

-- -----------------------------------------------------------
-- 12. 播放/阅读进度表（视频/音频/PDF/文本，跨设备断点续看）
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
-- 13. 随手记笔记表（便签/笔记，正文存 Markdown）
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
-- 14. 笔记阅读进度表（跨设备续读长笔记）
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

-- -----------------------------------------------------------
-- 15. 随手记笔记媒体表（图片/录音/画画，独立于文件中心存储）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_note_media` (
    `id`         VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `user_id`    VARCHAR(32)  NOT NULL COMMENT '媒体所有者用户 ID',
    `media_type` VARCHAR(16)  NOT NULL COMMENT '媒体类型：IMAGE（图片）/ AUDIO（录音）/ DRAWING（画画）',
    `file_name`  VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `mime_type`  VARCHAR(100) NOT NULL COMMENT 'Content-Type（如 image/png、audio/mp4）',
    `size_bytes` BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='随手记笔记媒体表（图片/录音/画画，独立于文件中心存储）';

-- -----------------------------------------------------------
-- 16. 登录会话表（长会话 token + 设备信息，吊销驱动 + 不活跃兜底）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bf_auth_session` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '主键，UUID',
    `user_id`       VARCHAR(32)  NOT NULL COMMENT '用户 ID',
    `device_name`   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '设备名（App 机型 / Web 浏览器摘要）',
    `device_type`   VARCHAR(16)  NOT NULL COMMENT '设备类型：ANDROID / WEB',
    `ip`            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '最近登录 IP',
    `user_agent`    VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'User-Agent',
    `token_hash`    VARCHAR(64)  NOT NULL COMMENT '会话 token 的 SHA-256（十六进制）',
    `expires_at`    TIMESTAMP    NOT NULL COMMENT '会话到期时间（ANDROID 滑动续期 / WEB 固定）',
    `last_used_at`  TIMESTAMP    NOT NULL COMMENT '最近使用时间（滑动续期基准）',
    `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `revoked_at`    TIMESTAMP    NULL COMMENT '吊销时间（非空 = 已登出 / 被强制下线）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_hash` (`token_hash`),
    KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话表（长会话 token + 设备信息，吊销驱动 + 不活跃兜底）';
