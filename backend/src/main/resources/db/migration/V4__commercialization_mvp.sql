ALTER TABLE ingredients
    ADD COLUMN code VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN category VARCHAR(120) NOT NULL DEFAULT 'Chưa phân loại',
    ADD COLUMN max_stock DECIMAL(14,3) NOT NULL DEFAULT 0,
    ADD COLUMN unit_cost DECIMAL(14,3) NOT NULL DEFAULT 0;

UPDATE ingredients SET code = CONCAT('ING-', id) WHERE code = '';

ALTER TABLE inventory_batches
    ADD COLUMN batch_number VARCHAR(120) NOT NULL DEFAULT '',
    ADD COLUMN cost_per_unit DECIMAL(14,3) NOT NULL DEFAULT 0;

UPDATE inventory_batches SET batch_number = CONCAT('BATCH-', id) WHERE batch_number = '';

ALTER TABLE stock_transactions
    ADD COLUMN reason VARCHAR(40) NOT NULL DEFAULT '',
    ADD COLUMN unit_cost DECIMAL(14,3) NOT NULL DEFAULT 0,
    ADD COLUMN waste_reason VARCHAR(40) NULL;

UPDATE stock_transactions
SET reason = CASE WHEN type = 'IN' THEN 'IMPORT_NEW' ELSE 'EXPORT_CONSUME' END
WHERE reason = '';

ALTER TABLE waste_records
    ADD COLUMN batch_id BIGINT NULL,
    ADD COLUMN estimated_cost DECIMAL(14,3) NOT NULL DEFAULT 0,
    ADD COLUMN created_by BIGINT NULL,
    ADD CONSTRAINT fk_waste_records_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
    ADD CONSTRAINT fk_waste_records_user FOREIGN KEY (created_by) REFERENCES users(id);

CREATE INDEX idx_stock_transactions_store_created ON stock_transactions (store_id, created_at);
CREATE INDEX idx_waste_store_ingredient_created ON waste_records (store_id, ingredient_id, created_at);
