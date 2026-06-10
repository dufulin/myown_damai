CREATE TABLE IF NOT EXISTS user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(88) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_sessions_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);
