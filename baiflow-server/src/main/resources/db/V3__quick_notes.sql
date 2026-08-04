-- 随手记（便签/笔记）：bf_note 存笔记元数据与 Markdown 正文，bf_note_progress 存跨设备阅读进度
CREATE TABLE IF NOT EXISTS bf_note (
    id         VARCHAR(32)  NOT NULL,
    user_id    VARCHAR(32)  NOT NULL,
    title      VARCHAR(200) NOT NULL DEFAULT '',
    content    LONGTEXT     NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP    NULL,
    PRIMARY KEY (id),
    KEY idx_user_updated (user_id, updated_at)
);

CREATE TABLE IF NOT EXISTS bf_note_progress (
    id             VARCHAR(32) NOT NULL,
    user_id        VARCHAR(32) NOT NULL,
    note_id        VARCHAR(32) NOT NULL,
    position_type  VARCHAR(16) NOT NULL DEFAULT 'SCROLL_PERCENT',
    position_value DOUBLE      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_note (user_id, note_id)
);
