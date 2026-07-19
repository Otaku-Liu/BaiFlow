CREATE TABLE IF NOT EXISTS system_bootstrap (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bootstrap_key VARCHAR(64) NOT NULL,
    bootstrap_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_bootstrap_key (bootstrap_key)
);

INSERT INTO system_bootstrap (bootstrap_key, bootstrap_value)
VALUES ('phase', 'phase-1')
ON DUPLICATE KEY UPDATE bootstrap_value = VALUES(bootstrap_value);
