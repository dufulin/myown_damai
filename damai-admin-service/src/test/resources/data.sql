INSERT INTO d_user (id, name, mobile, email, create_time, status)
VALUES
    (1, 'Admin', '18800000001', 'admin@example.com', '2026-06-29 09:00:00', 1),
    (2, 'Customer', '18800000002', 'customer@example.com', '2026-06-29 09:10:00', 1);

INSERT INTO d_user_role (user_id, role, create_time, edit_time)
VALUES
    (1, 'ADMIN', '2026-06-29 09:00:00', '2026-06-29 09:00:00'),
    (2, 'USER', '2026-06-29 09:10:00', '2026-06-29 09:10:00');

INSERT INTO d_program (
    id, title, area_id, program_category_id, program_status, issue_time, create_time, status
)
VALUES
    (10, 'Active Concert', 110000, 2, 1, '2026-06-29 08:00:00', '2026-06-29 08:00:00', 1),
    (11, 'Offline Concert', 310000, 2, 0, '2026-06-28 08:00:00', '2026-06-28 08:00:00', 1);

INSERT INTO d_ticket_category (id, program_id, total_number, remain_number, status)
VALUES
    (100, 10, 100, 80, 1),
    (101, 10, 50, 20, 1),
    (102, 11, 20, 20, 1);

INSERT INTO d_order (
    id, order_number, user_id, program_id, program_title, order_price, order_status,
    expire_time, create_order_time, pay_order_time, status
)
VALUES
    (1000, 90001, 2, 10, 'Active Concert', 680.00, 1, '2026-06-29 10:00:00', '2026-06-29 09:20:00', NULL, 1),
    (1001, 90002, 2, 10, 'Active Concert', 680.00, 3, '2026-06-29 10:00:00', '2026-06-29 09:21:00', '2026-06-29 09:22:00', 1),
    (1002, 90003, 2, 11, 'Offline Concert', 380.00, 5, '2026-06-29 09:00:00', '2026-06-29 08:30:00', NULL, 1);

INSERT INTO d_pay_bill (id, pay_amount, pay_bill_status, status)
VALUES
    (2000, 680.00, 2, 1),
    (2001, 380.00, 1, 1);
