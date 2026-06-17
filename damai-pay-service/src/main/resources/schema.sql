CREATE TABLE IF NOT EXISTS d_pay_bill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pay_number VARCHAR(64),
    out_order_no VARCHAR(64) NOT NULL,
    pay_channel VARCHAR(64),
    pay_scene VARCHAR(64),
    subject VARCHAR(512),
    trade_number VARCHAR(256),
    pay_amount DECIMAL(10, 2) NOT NULL,
    pay_bill_type INT NOT NULL,
    pay_bill_status INT NOT NULL DEFAULT 1,
    pay_time DATETIME(6),
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_d_pay_bill_out_order_no UNIQUE (out_order_no),
    INDEX idx_d_pay_bill_pay_number (pay_number),
    INDEX idx_d_pay_bill_trade_number (trade_number)
);

CREATE TABLE IF NOT EXISTS d_refund_bill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    out_order_no VARCHAR(64) NOT NULL,
    pay_bill_id BIGINT NOT NULL,
    refund_amount DECIMAL(10, 2) NOT NULL,
    refund_status INT NOT NULL DEFAULT 1,
    refund_time DATETIME(6),
    reason VARCHAR(50),
    create_time DATETIME(6) NOT NULL,
    edit_time DATETIME(6) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_d_refund_bill_out_order_no UNIQUE (out_order_no),
    CONSTRAINT fk_d_refund_bill_pay_bill_id FOREIGN KEY (pay_bill_id) REFERENCES d_pay_bill (id)
);
