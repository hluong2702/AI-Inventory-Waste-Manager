-- Normalize historical rows to the tenant of the concrete inventory resource before
-- adding composite foreign keys. Batch is authoritative when a transaction references one.
UPDATE inventory_batches batch
JOIN ingredients ingredient ON ingredient.id = batch.ingredient_id
SET batch.store_id = ingredient.store_id
WHERE batch.store_id <> ingredient.store_id;

UPDATE stock_transactions transaction_row
JOIN inventory_batches batch ON batch.id = transaction_row.batch_id
SET transaction_row.store_id = batch.store_id,
    transaction_row.ingredient_id = batch.ingredient_id
WHERE transaction_row.batch_id IS NOT NULL
  AND (
      transaction_row.store_id <> batch.store_id
      OR transaction_row.ingredient_id <> batch.ingredient_id
  );

UPDATE stock_transactions transaction_row
JOIN ingredients ingredient ON ingredient.id = transaction_row.ingredient_id
SET transaction_row.store_id = ingredient.store_id
WHERE transaction_row.batch_id IS NULL
  AND transaction_row.store_id <> ingredient.store_id;

UPDATE waste_records waste
JOIN inventory_batches batch ON batch.id = waste.batch_id
SET waste.store_id = batch.store_id,
    waste.ingredient_id = batch.ingredient_id
WHERE waste.batch_id IS NOT NULL
  AND (
      waste.store_id <> batch.store_id
      OR waste.ingredient_id <> batch.ingredient_id
  );

UPDATE waste_records waste
JOIN ingredients ingredient ON ingredient.id = waste.ingredient_id
SET waste.store_id = ingredient.store_id
WHERE waste.batch_id IS NULL
  AND waste.store_id <> ingredient.store_id;

UPDATE alerts alert_row
JOIN ingredients ingredient ON ingredient.id = alert_row.ingredient_id
SET alert_row.store_id = ingredient.store_id
WHERE alert_row.store_id <> ingredient.store_id;

UPDATE daily_actions action_row
JOIN inventory_batches batch ON batch.id = action_row.batch_id
SET action_row.tenant_id = batch.store_id,
    action_row.product_id = batch.ingredient_id
WHERE action_row.batch_id IS NOT NULL
  AND (
      action_row.tenant_id <> batch.store_id
      OR action_row.product_id <> batch.ingredient_id
  );

UPDATE daily_actions action_row
JOIN ingredients ingredient ON ingredient.id = action_row.product_id
SET action_row.tenant_id = ingredient.store_id
WHERE action_row.batch_id IS NULL
  AND action_row.tenant_id <> ingredient.store_id;

ALTER TABLE ingredients
    ADD UNIQUE KEY uk_ingredients_store_id (store_id, id);

ALTER TABLE inventory_batches
    ADD UNIQUE KEY uk_batches_store_ingredient_id (store_id, ingredient_id, id),
    ADD CONSTRAINT fk_batches_ingredient_same_store
        FOREIGN KEY (store_id, ingredient_id)
        REFERENCES ingredients (store_id, id);

ALTER TABLE stock_transactions
    ADD CONSTRAINT fk_transactions_ingredient_same_store
        FOREIGN KEY (store_id, ingredient_id)
        REFERENCES ingredients (store_id, id),
    ADD CONSTRAINT fk_transactions_batch_same_store_ingredient
        FOREIGN KEY (store_id, ingredient_id, batch_id)
        REFERENCES inventory_batches (store_id, ingredient_id, id);

ALTER TABLE waste_records
    ADD CONSTRAINT fk_waste_ingredient_same_store
        FOREIGN KEY (store_id, ingredient_id)
        REFERENCES ingredients (store_id, id),
    ADD CONSTRAINT fk_waste_batch_same_store_ingredient
        FOREIGN KEY (store_id, ingredient_id, batch_id)
        REFERENCES inventory_batches (store_id, ingredient_id, id);

ALTER TABLE alerts
    ADD CONSTRAINT fk_alerts_ingredient_same_store
        FOREIGN KEY (store_id, ingredient_id)
        REFERENCES ingredients (store_id, id);

ALTER TABLE daily_actions
    ADD CONSTRAINT fk_daily_actions_product_same_tenant
        FOREIGN KEY (tenant_id, product_id)
        REFERENCES ingredients (store_id, id),
    ADD CONSTRAINT fk_daily_actions_batch_same_tenant_product
        FOREIGN KEY (tenant_id, product_id, batch_id)
        REFERENCES inventory_batches (store_id, ingredient_id, id);

UPDATE alerts duplicate_alert
JOIN (
    SELECT store_id, type, ingredient_id, MAX(id) AS keep_id
    FROM alerts
    WHERE resolved = FALSE
    GROUP BY store_id, type, ingredient_id
    HAVING COUNT(*) > 1
) alert_to_keep
    ON alert_to_keep.store_id = duplicate_alert.store_id
   AND alert_to_keep.type = duplicate_alert.type
   AND alert_to_keep.ingredient_id = duplicate_alert.ingredient_id
SET duplicate_alert.resolved = TRUE
WHERE duplicate_alert.resolved = FALSE
  AND duplicate_alert.id <> alert_to_keep.keep_id;

ALTER TABLE alerts
    ADD COLUMN open_guard TINYINT
        GENERATED ALWAYS AS (CASE WHEN resolved = FALSE THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_alerts_one_open_condition (store_id, type, ingredient_id, open_guard);
