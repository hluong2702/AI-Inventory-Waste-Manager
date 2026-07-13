UPDATE stores SET owner_id = NULL WHERE id IN (1, 2, 3);
UPDATE users SET store_id = NULL WHERE id IN (1, 2, 3, 4, 5);

DELETE FROM payment_transactions WHERE tenant_id IN (1, 2, 3);
DELETE FROM tenant_subscriptions WHERE tenant_id IN (1, 2, 3);
DELETE FROM invite_tokens WHERE user_id IN (1, 2, 3, 4, 5);
DELETE FROM alerts WHERE store_id IN (1, 2, 3);
DELETE FROM waste_records WHERE store_id IN (1, 2, 3);
DELETE FROM stock_transactions WHERE store_id IN (1, 2, 3);
DELETE FROM inventory_batches WHERE store_id IN (1, 2, 3);
DELETE FROM ingredients WHERE store_id IN (1, 2, 3);
DELETE FROM subscriptions WHERE store_id IN (1, 2, 3);
DELETE FROM stores WHERE id IN (1, 2, 3);
DELETE FROM users WHERE id IN (1, 2, 3, 4, 5);
