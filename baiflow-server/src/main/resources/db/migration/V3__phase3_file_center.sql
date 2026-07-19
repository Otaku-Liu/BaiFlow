-- Phase 3: File Center MVP — storage_root, file_item, user_storage_permission

CREATE TABLE IF NOT EXISTS storage_root (
    id          VARCHAR(32)  NOT NULL,
    name        VARCHAR(128) NOT NULL DEFAULT '',
    type        VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL, NAS_MOUNT',
    root_path   VARCHAR(512) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, OFFLINE, DISABLED',
    readonly    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_storage_root_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS file_item (
    id               VARCHAR(32)  NOT NULL,
    storage_root_id  VARCHAR(32)  NOT NULL,
    parent_id        VARCHAR(32)  NULL,
    owner_user_id    VARCHAR(32)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    relative_path    VARCHAR(1024) NOT NULL DEFAULT '',
    item_type        VARCHAR(16)  NOT NULL DEFAULT 'FILE' COMMENT 'FILE, DIRECTORY',
    size_bytes       BIGINT       NOT NULL DEFAULT 0,
    mime_type        VARCHAR(128) NOT NULL DEFAULT '',
    hash_sha256      VARCHAR(128) NOT NULL DEFAULT '',
    privacy_mode     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL, PRIVATE',
    privacy_password_hash VARCHAR(255) NOT NULL DEFAULT '',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DELETED',
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP NULL,
    PRIMARY KEY (id),
    KEY idx_file_item_storage_parent (storage_root_id, parent_id),
    KEY idx_file_item_storage_path (storage_root_id, relative_path(255)),
    KEY idx_file_item_owner (owner_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_storage_permission (
    id              VARCHAR(32)  NOT NULL,
    user_id         VARCHAR(32)  NOT NULL,
    storage_root_id VARCHAR(32)  NOT NULL,
    file_item_id    VARCHAR(32)  NULL COMMENT 'NULL = entire root; set = specific dir/file',
    permission      VARCHAR(16)  NOT NULL DEFAULT 'READ' COMMENT 'READ, WRITE, MANAGE',
    created_by      VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_usp_user_root (user_id, storage_root_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
