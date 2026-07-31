-- ============================================================
-- BaiFlow 初始化数据库脚本
-- 包含所有表结构（含完整字段和表注释）及初始数据
-- 适用于全新部署，可重复执行（使用 IF NOT EXISTS）
-- ============================================================

-- -----------------------------------------------------------
-- 1. 系统引导配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_bootstrap` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    `bootstrap_key`  VARCHAR(64)  NOT NULL COMMENT '配置键',
    `bootstrap_value` VARCHAR(255) NOT NULL COMMENT '配置值',
    `created_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_bootstrap_key` (`bootstrap_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统引导配置表';

INSERT INTO `system_bootstrap` (`bootstrap_key`, `bootstrap_value`)
VALUES ('phase', 'initialized')
ON DUPLICATE KEY UPDATE `bootstrap_value` = VALUES(`bootstrap_value`);

-- -----------------------------------------------------------
-- 2. 系统用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '用户主键，UUID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录用户名，全局唯一',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希后的密码，绝不存明文',
    `display_name`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '显示名称',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER / GUEST',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE（正常）/ DISABLED（禁用）/ LOCKED（锁定）',
    `avatar_url`    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像访问 URL（nginx 静态文件链接）',
    `last_login_at` TIMESTAMP    NULL COMMENT '最后登录时间',
    `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 默认管理员账号（用户名 admin，密码 admin）
INSERT INTO `user` (`id`, `username`, `password_hash`, `display_name`, `role`, `status`, `avatar_url`, `created_at`, `updated_at`)
SELECT REPLACE(UUID(), '-', ''), 'admin',
       '$2a$10$J56W4KahX.odv.j2jNdzie00DVgxql0Lo4Fc3P6LUTz9iwIdEexQW',
       'Administrator', 'ADMIN', 'ACTIVE', '', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'admin');

-- -----------------------------------------------------------
-- 3. 存储根目录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `storage_root` (
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
-- 4. 文件项表（文件和目录元数据）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_item` (
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
-- 5. 用户存储权限表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_storage_permission` (
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
-- 6. 隐私文件夹访问会话表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `private_folder_access` (
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
-- 7. 传输任务表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transfer_task` (
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
-- 8. 用户通知表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
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
-- 9. 下载任务表（aria2 下载管理）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `download_task` (
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
-- 10. 分享链接表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `share_link` (
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
-- 11. 分享访问日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `share_access_log` (
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
-- 12. 操作审计日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
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
