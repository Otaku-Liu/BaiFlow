-- Phase 9: Security & Audit — audit_log 表

CREATE TABLE IF NOT EXISTS audit_log (
    id            VARCHAR(32)   NOT NULL COMMENT '主键，UUID 自动生成',
    actor_user_id VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '操作者用户 ID（匿名操作为空）',
    action        VARCHAR(64)   NOT NULL COMMENT '操作类型：LOGIN_SUCCESS / LOGIN_FAILED / FILE_DELETE / SHARE_CREATE / SHARE_ACCESS / SHARE_REVOKE 等',
    target_type   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '操作目标类型：USER / FILE / SHARE_LINK 等',
    target_id     VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '操作目标 ID',
    ip_address    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '操作者 IP 地址',
    user_agent    VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '操作者 User-Agent',
    detail        VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '操作详情（补充描述）',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_al_actor (actor_user_id, created_at),
    KEY idx_al_action (action, created_at),
    KEY idx_al_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';
