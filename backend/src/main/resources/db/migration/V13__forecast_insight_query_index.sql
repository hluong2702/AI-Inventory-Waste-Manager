CREATE INDEX idx_stock_transactions_consumption_aggregate
    ON stock_transactions (store_id, type, reason, ingredient_id, created_at);
