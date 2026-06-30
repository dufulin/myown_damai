CREATE TABLE d_user (
    id BIGINT PRIMARY KEY,
    name VARCHAR(256),
    mobile VARCHAR(191) NOT NULL,
    email VARCHAR(191),
    create_time TIMESTAMP NOT NULL,
    status TINYINT NOT NULL
);

CREATE TABLE d_user_role (
    user_id BIGINT PRIMARY KEY,
    role VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    edit_time TIMESTAMP NOT NULL
);

CREATE TABLE d_program (
    id BIGINT PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    area_id BIGINT NOT NULL,
    program_category_id BIGINT NOT NULL,
    program_status TINYINT NOT NULL,
    issue_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    status TINYINT NOT NULL
);

CREATE TABLE d_ticket_category (
    id BIGINT PRIMARY KEY,
    program_id BIGINT NOT NULL,
    total_number BIGINT NOT NULL,
    remain_number BIGINT NOT NULL,
    status TINYINT NOT NULL
);

CREATE TABLE d_order (
    id BIGINT PRIMARY KEY,
    order_number BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    program_title VARCHAR(512),
    order_price DECIMAL(10, 2) NOT NULL,
    order_status INT NOT NULL,
    expire_time TIMESTAMP NOT NULL,
    create_order_time TIMESTAMP NOT NULL,
    pay_order_time TIMESTAMP,
    status TINYINT NOT NULL
);

CREATE TABLE d_pay_bill (
    id BIGINT PRIMARY KEY,
    pay_amount DECIMAL(10, 2) NOT NULL,
    pay_bill_status INT NOT NULL,
    status TINYINT NOT NULL
);
