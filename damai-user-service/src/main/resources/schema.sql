CREATE TABLE IF NOT EXISTS d_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(256),
    rel_name VARCHAR(256),
    mobile VARCHAR(191) NOT NULL,
    gender INT NOT NULL DEFAULT 1,
    password VARCHAR(512),
    email_status TINYINT NOT NULL DEFAULT 0,
    email VARCHAR(191),
    rel_authentication_status TINYINT NOT NULL DEFAULT 0,
    id_number VARCHAR(512),
    address VARCHAR(256),
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_d_user_mobile UNIQUE (mobile),
    CONSTRAINT uk_d_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS d_user_mobile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    mobile VARCHAR(191) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_d_user_mobile_mobile UNIQUE (mobile),
    CONSTRAINT fk_d_user_mobile_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

CREATE TABLE IF NOT EXISTS d_user_role (
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    INDEX idx_d_user_role_role (role, user_id),
    CONSTRAINT fk_d_user_role_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

CREATE TABLE IF NOT EXISTS d_user_email (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    email VARCHAR(191) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_d_user_email_email UNIQUE (email),
    CONSTRAINT fk_d_user_email_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

CREATE TABLE IF NOT EXISTS d_ticket_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    rel_name VARCHAR(256) NOT NULL,
    id_type INT NOT NULL DEFAULT 1,
    id_number VARCHAR(512) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_d_ticket_user_user_status_time (user_id, status, create_time, id),
    CONSTRAINT fk_d_ticket_user_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

CREATE TABLE IF NOT EXISTS d_user_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(88) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_d_user_sessions_expiry (expires_at, revoked_at),
    CONSTRAINT uk_d_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_d_user_sessions_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

CREATE TABLE IF NOT EXISTS d_user_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(88) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_d_user_refresh_tokens_expiry (expires_at, revoked_at),
    CONSTRAINT uk_d_user_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_d_user_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);
