-- Phase 8: Share URL & Access Control — share_link, share_access_log

CREATE TABLE IF NOT EXISTS share_link (
    id                       VARCHAR(32)   NOT NULL COMMENT '主键，UUID 自动生成',
    target_file_item_id      VARCHAR(32)   NOT NULL COMMENT '被分享的文件或文件夹 ID',
    created_by               VARCHAR(32)   NOT NULL COMMENT '创建者用户 ID',
    token_hash               VARCHAR(255)  NOT NULL COMMENT 'BCrypt 哈希后的分享 token',
    extraction_code_hash     VARCHAR(255)  NOT NULL DEFAULT '' COMMENT 'BCrypt 哈希后的提取码（空字符串表示未设置提取码）',
    share_type               VARCHAR(16)   NOT NULL DEFAULT 'FILE' COMMENT '分享类型：FILE / FOLDER',
    access_mode              VARCHAR(16)   NOT NULL DEFAULT 'VIEW' COMMENT '访问模式：VIEW（浏览）/ DOWNLOAD（可下载）',
    expires_at               TIMESTAMP     NULL COMMENT '过期时间（NULL 表示永不过期）',
    max_views                INT           NOT NULL DEFAULT 0 COMMENT '最大访问次数（0 表示不限制）',
    view_count               INT           NOT NULL DEFAULT 0 COMMENT '已访问次数',
    max_downloads            INT           NOT NULL DEFAULT 0 COMMENT '最大下载次数（0 表示不限制）',
    download_count           INT           NOT NULL DEFAULT 0 COMMENT '已下载次数',
    require_private_password TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否需要隐私文件夹密码（分享目标是隐私文件夹时为 1）',
    status                   VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / EXPIRED / REVOKED',
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sl_token_hash (token_hash(64)),
    KEY idx_sl_created_by (created_by, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享链接';

CREATE TABLE IF NOT EXISTS share_access_log (
    id             VARCHAR(32)   NOT NULL COMMENT '主键，UUID 自动生成',
    share_link_id  VARCHAR(32)   NOT NULL COMMENT '分享链接 ID',
    action         VARCHAR(32)   NOT NULL DEFAULT 'VIEW' COMMENT '操作类型：VIEW / DOWNLOAD / VERIFY_CODE / FAILED',
    ip_address     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '访问者 IP 地址',
    user_agent     VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '访问者 User-Agent',
    success        TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否成功（1=成功，0=失败）',
    failure_reason VARCHAR(256)  NOT NULL DEFAULT '' COMMENT '失败原因（success=0 时填写）',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sal_share_link (share_link_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享访问日志';
