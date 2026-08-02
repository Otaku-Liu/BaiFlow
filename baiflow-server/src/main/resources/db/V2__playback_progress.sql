CREATE TABLE IF NOT EXISTS bf_playback_progress (
    id             VARCHAR(32) NOT NULL,
    user_id        VARCHAR(32) NOT NULL,
    file_item_id   VARCHAR(32) NOT NULL,
    position_type  VARCHAR(16) NOT NULL DEFAULT 'SECONDS',
    position_value DOUBLE      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_file (user_id, file_item_id),
    KEY idx_user (user_id, updated_at)
);
