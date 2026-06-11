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
    CONSTRAINT uk_d_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_d_user_sessions_user_id FOREIGN KEY (user_id) REFERENCES d_user (id)
);

CREATE TABLE IF NOT EXISTS d_program_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(120) NOT NULL,
    type INT NOT NULL DEFAULT 2,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_d_program_category_parent_name_type UNIQUE (parent_id, name, type)
);

CREATE TABLE IF NOT EXISTS d_program_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_json TEXT NOT NULL,
    recent_show_time DATETIME(6) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS d_program (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_group_id BIGINT NOT NULL,
    prime TINYINT NOT NULL DEFAULT 1,
    area_id BIGINT NOT NULL,
    program_category_id BIGINT NOT NULL,
    parent_program_category_id BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL,
    actor VARCHAR(256),
    place VARCHAR(100),
    item_picture TEXT,
    pre_sell TINYINT NOT NULL DEFAULT 0,
    pre_sell_instruction VARCHAR(256),
    important_notice VARCHAR(100),
    detail TEXT NOT NULL,
    per_order_limit_purchase_count INT DEFAULT 6,
    per_account_limit_purchase_count INT DEFAULT 6,
    refund_ticket_rule VARCHAR(512),
    delivery_instruction VARCHAR(512),
    entry_rule VARCHAR(512),
    child_purchase VARCHAR(512),
    invoice_specification VARCHAR(512),
    real_ticket_purchase_rule TEXT,
    abnormal_order_description TEXT,
    kind_reminder TEXT,
    performance_duration VARCHAR(100),
    entry_time VARCHAR(512),
    min_performance_count INT,
    main_actor VARCHAR(100),
    min_performance_duration VARCHAR(100),
    prohibited_item TEXT,
    deposit_specification VARCHAR(512),
    total_count BIGINT,
    permit_refund TINYINT NOT NULL DEFAULT 0,
    refund_explain VARCHAR(512),
    rel_name_ticket_entrance TINYINT NOT NULL DEFAULT 0,
    rel_name_ticket_entrance_explain VARCHAR(512),
    permit_choose_seat TINYINT NOT NULL DEFAULT 0,
    choose_seat_explain VARCHAR(512),
    electronic_delivery_ticket TINYINT NOT NULL DEFAULT 1,
    electronic_delivery_ticket_explain VARCHAR(512),
    electronic_invoice TINYINT NOT NULL DEFAULT 1,
    electronic_invoice_explain VARCHAR(512),
    high_heat TINYINT NOT NULL DEFAULT 0,
    program_status TINYINT NOT NULL DEFAULT 1,
    issue_time DATETIME(6),
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_d_program_group_id (program_group_id),
    INDEX idx_d_program_area_id (area_id),
    INDEX idx_d_program_issue_time (issue_time),
    CONSTRAINT fk_d_program_group_id FOREIGN KEY (program_group_id) REFERENCES d_program_group (id),
    CONSTRAINT fk_d_program_category_id FOREIGN KEY (program_category_id) REFERENCES d_program_category (id),
    CONSTRAINT fk_d_program_parent_category_id FOREIGN KEY (parent_program_category_id) REFERENCES d_program_category (id)
);

CREATE TABLE IF NOT EXISTS d_program_show_time (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    show_time DATETIME(6) NOT NULL,
    show_day_time DATETIME(6),
    show_week_time VARCHAR(64) NOT NULL,
    area_id BIGINT,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_d_program_show_time_program_id (program_id),
    INDEX idx_d_program_show_time_show_time (show_time),
    INDEX idx_d_program_show_time_show_day_time (show_day_time),
    CONSTRAINT fk_d_program_show_time_program_id FOREIGN KEY (program_id) REFERENCES d_program (id)
);

CREATE TABLE IF NOT EXISTS d_ticket_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    introduce VARCHAR(256) NOT NULL,
    price DECIMAL(10, 0) NOT NULL,
    total_number BIGINT NOT NULL,
    remain_number BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_d_ticket_category_program_id (program_id),
    CONSTRAINT fk_d_ticket_category_program_id FOREIGN KEY (program_id) REFERENCES d_program (id)
);

CREATE TABLE IF NOT EXISTS d_seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    ticket_category_id BIGINT NOT NULL,
    row_code INT NOT NULL,
    col_code INT NOT NULL,
    seat_type INT NOT NULL,
    price DECIMAL(10, 0) NOT NULL,
    sell_status INT NOT NULL DEFAULT 1,
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_d_seat_program_id (program_id),
    INDEX idx_d_seat_row_code (row_code),
    INDEX idx_d_seat_col_code (col_code),
    CONSTRAINT fk_d_seat_program_id FOREIGN KEY (program_id) REFERENCES d_program (id),
    CONSTRAINT fk_d_seat_ticket_category_id FOREIGN KEY (ticket_category_id) REFERENCES d_ticket_category (id)
);
