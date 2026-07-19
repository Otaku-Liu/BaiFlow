-- Phase 3.5: Privacy Folder — private_folder_access 短期访问会话表

CREATE TABLE IF NOT EXISTS private_folder_access (
    id                VARCHAR(32)  NOT NULL COMMENT '主键',
    user_id           VARCHAR(32)  NOT NULL COMMENT '访问用户 ID',
    file_item_id      VARCHAR(32)  NOT NULL COMMENT '隐私文件夹 ID',
    access_token_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希后的短期访问令牌',
    expires_at        TIMESTAMP    NOT NULL COMMENT '会话过期时间',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_pfa_user_folder (user_id, file_item_id),
    KEY idx_pfa_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
