ALTER TABLE ingredients
    ADD CONSTRAINT chk_ingredients_min_stock_nonnegative CHECK (min_stock >= 0),
    ADD CONSTRAINT chk_ingredients_max_stock_nonnegative CHECK (max_stock >= 0),
    ADD CONSTRAINT chk_ingredients_unit_cost_nonnegative CHECK (unit_cost >= 0),
    ADD CONSTRAINT chk_ingredients_stock_range CHECK (max_stock = 0 OR max_stock >= min_stock);

ALTER TABLE inventory_batches
    ADD CONSTRAINT chk_inventory_batches_quantity_nonnegative CHECK (quantity >= 0),
    ADD CONSTRAINT chk_inventory_batches_cost_nonnegative CHECK (cost_per_unit >= 0);

ALTER TABLE stock_transactions
    ADD CONSTRAINT chk_stock_transactions_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT chk_stock_transactions_cost_nonnegative CHECK (unit_cost >= 0),
    ADD CONSTRAINT chk_stock_transactions_type CHECK (type IN ('IN', 'OUT')),
    ADD CONSTRAINT chk_stock_transactions_reason CHECK (
        (type = 'IN' AND reason = 'IMPORT_NEW')
        OR (type = 'OUT' AND reason IN ('EXPORT_CONSUME', 'EXPORT_WASTE', 'EXPORT_ADJUST'))
    );

ALTER TABLE waste_records
    ADD CONSTRAINT chk_waste_records_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT chk_waste_records_cost_nonnegative CHECK (estimated_cost >= 0);

CREATE INDEX idx_batches_store_ingredient_expiry_v2
    ON inventory_batches (store_id, ingredient_id, expiry_date, received_at, id);

DROP INDEX idx_batches_store_ingredient_expiry ON inventory_batches;

ALTER TABLE inventory_batches
    RENAME INDEX idx_batches_store_ingredient_expiry_v2 TO idx_batches_store_ingredient_expiry;
