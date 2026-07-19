CREATE TABLE IF NOT EXISTS `user` (
    id          VARCHAR(32)  NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL DEFAULT '',
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN, USER, GUEST',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DISABLED, LOCKED',
    last_login_at TIMESTAMP NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `user` (id, username, password_hash, display_name, role, status, created_at, updated_at)
VALUES (
    REPLACE(UUID(), '-', ''),
    'admin',
    -- BCrypt hash of 'admin'
    '$2a$10$J56W4KahX.odv.j2jNdzie00DVgxql0Lo4Fc3P6LUTz9iwIdEexQW',
    'Administrator',
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
);
