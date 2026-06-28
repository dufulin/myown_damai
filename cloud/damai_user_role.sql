CREATE TABLE IF NOT EXISTS d_user_role (
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    INDEX idx_d_user_role_role (role, user_id),
    CONSTRAINT fk_d_user_role_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

-- Replace the mobile before executing this statement to bootstrap the first administrator.
INSERT INTO d_user_role (user_id, role, create_time, edit_time)
SELECT id, 'ADMIN', NOW(6), NOW(6)
FROM d_user
WHERE mobile = 'REPLACE_WITH_ADMIN_MOBILE'
ON DUPLICATE KEY UPDATE role = 'ADMIN', edit_time = NOW(6);
