INSERT INTO stores (id, name, address, subscription_plan, status, created_at) VALUES
  (1, 'Coffee A - Nguyen Hue', '12 Nguyen Hue, District 1, Ho Chi Minh City', 'FREE', 'ACTIVE', '2026-07-09 08:00:00'),
  (2, 'Coffee B - Thao Dien', '21 Quoc Huong, Thu Duc City', 'BASIC', 'ACTIVE', '2026-07-09 08:05:00'),
  (3, 'Bep Chay An Nhien', '18 Phan Xich Long, Phu Nhuan', 'PRO', 'ACTIVE', '2026-07-09 08:10:00');

INSERT INTO subscriptions (id, store_id, plan, max_staff, max_ingredients, expires_at, active, created_at) VALUES
  (1, 1, 'FREE', 2, 30, '2026-08-09', TRUE, '2026-07-09 08:00:00'),
  (2, 2, 'BASIC', 10, 500, '2026-09-09', TRUE, '2026-07-09 08:05:00'),
  (3, 3, 'PRO', NULL, NULL, '2027-01-09', TRUE, '2026-07-09 08:10:00');

INSERT INTO users (id, store_id, full_name, email, password_hash, role, status, must_change_password, created_at) VALUES
  (1, NULL, 'System Admin', 'admin@inventoryai.vn', '$2a$10$8tWWLlB3peWp.6qVbfF/yOsqIICp8ZT.Cm2AhvMgM7PJ6ouuI5t2e', 'SYSTEM_ADMIN', 'ACTIVE', FALSE, '2026-07-09 08:00:00'),
  (2, 1, 'Nguyen Van Owner', 'owner@coffee.vn', '$2a$10$ctrGCOer1SxxAjWtvf8z5emnx8Xb6fB7eNNC7jNT65gwKV1o0WiH6', 'OWNER', 'ACTIVE', FALSE, '2026-07-09 08:01:00'),
  (3, 1, 'Le Thi Manager', 'manager@coffee.vn', '$2a$10$qFZR44XVLu6fsyXJgglRQuv./Oo3PcVRqkCrKakNVc/WhxdYdvwDW', 'MANAGER', 'ACTIVE', FALSE, '2026-07-09 08:02:00'),
  (4, 1, 'Tran Thi Staff', 'staff@coffee.vn', '$2a$10$fJGBHxJ1IJvtfJWk9UN5P.u4BNKWb/K3xesPO9tVYjOdCXs2AqgA2', 'STAFF', 'ACTIVE', FALSE, '2026-07-09 08:03:00'),
  (5, 2, 'Coffee B Owner', 'ownerb@coffee.vn', '$2a$10$ctrGCOer1SxxAjWtvf8z5emnx8Xb6fB7eNNC7jNT65gwKV1o0WiH6', 'OWNER', 'ACTIVE', FALSE, '2026-07-09 08:06:00');

INSERT INTO ingredients (id, store_id, name, unit, min_stock, is_deleted, created_at) VALUES
  (1, 1, 'Hat Cafe Arabica', 'kg', 20.000, FALSE, '2026-07-09 08:20:00'),
  (2, 1, 'Sua Tuoi Tiet Trung 1L', 'hop', 30.000, FALSE, '2026-07-09 08:21:00'),
  (3, 1, 'Syrup Duong Cat', 'chai', 10.000, FALSE, '2026-07-09 08:22:00'),
  (4, 1, 'Tran Chau Den', 'kg', 15.000, FALSE, '2026-07-09 08:23:00'),
  (5, 2, 'Hat Cafe Robusta', 'kg', 25.000, FALSE, '2026-07-09 08:24:00'),
  (6, 3, 'Dau Hu Non', 'kg', 10.000, FALSE, '2026-07-09 08:25:00');

INSERT INTO inventory_batches (id, store_id, ingredient_id, quantity, expiry_date, received_at) VALUES
  (1, 1, 1, 15.000, '2026-07-25', '2026-06-25 08:00:00'),
  (2, 1, 1, 25.000, '2026-08-20', '2026-07-01 08:00:00'),
  (3, 1, 2, 8.000, '2026-07-10', '2026-07-01 08:30:00'),
  (4, 1, 2, 4.000, '2026-07-08', '2026-06-25 08:30:00'),
  (5, 1, 3, 8.000, '2026-10-15', '2026-06-01 09:00:00'),
  (6, 1, 4, 3.000, '2026-07-11', '2026-07-05 09:00:00'),
  (7, 2, 5, 52.000, '2026-09-12', '2026-07-03 09:00:00'),
  (8, 3, 6, 12.000, '2026-07-12', '2026-07-08 09:00:00');

INSERT INTO stock_transactions (id, store_id, ingredient_id, batch_id, type, quantity, created_by, created_at) VALUES
  (1, 1, 1, 2, 'IN', 25.000, 2, '2026-07-01 08:30:00'),
  (2, 1, 2, 3, 'IN', 15.000, 2, '2026-07-01 08:35:00'),
  (3, 1, 2, 3, 'OUT', 7.000, 4, '2026-07-05 17:00:00'),
  (4, 1, 1, 1, 'OUT', 5.000, 4, '2026-07-06 17:30:00'),
  (5, 2, 5, 7, 'IN', 52.000, 5, '2026-07-03 09:15:00');

INSERT INTO waste_records (id, store_id, ingredient_id, quantity, reason, created_at) VALUES
  (1, 1, 2, 2.000, 'EXPIRED', '2026-07-08 18:00:00');

INSERT INTO alerts (id, store_id, type, ingredient_id, resolved, created_at) VALUES
  (1, 1, 'LOW_STOCK', 2, FALSE, '2026-07-09 09:00:00'),
  (2, 1, 'LOW_STOCK', 3, FALSE, '2026-07-09 09:01:00'),
  (3, 1, 'EXPIRING_SOON', 2, FALSE, '2026-07-09 09:02:00'),
  (4, 1, 'EXPIRING_SOON', 4, FALSE, '2026-07-09 09:03:00');
