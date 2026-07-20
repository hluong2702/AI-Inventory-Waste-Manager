-- Keep only the newest ACTIVE subscription before adding the database invariant.
UPDATE tenant_subscriptions duplicate_active
JOIN (
    SELECT tenant_id, MAX(id) AS keep_id
    FROM tenant_subscriptions
    WHERE status = 'ACTIVE'
    GROUP BY tenant_id
    HAVING COUNT(*) > 1
) active_to_keep ON active_to_keep.tenant_id = duplicate_active.tenant_id
SET duplicate_active.status = 'CANCELLED',
    duplicate_active.cancelled_at = COALESCE(duplicate_active.cancelled_at, CURRENT_TIMESTAMP)
WHERE duplicate_active.status = 'ACTIVE'
  AND duplicate_active.id <> active_to_keep.keep_id;

ALTER TABLE tenant_subscriptions
    ADD COLUMN active_tenant_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN tenant_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_tenant_subscriptions_single_active (active_tenant_id),
    ADD UNIQUE KEY uk_tenant_subscriptions_tenant_id (tenant_id, id);

-- Repair any historical mismatch before enforcing the composite tenant FK.
UPDATE payment_transactions payment
JOIN tenant_subscriptions subscription ON subscription.id = payment.subscription_id
SET payment.tenant_id = subscription.tenant_id
WHERE payment.tenant_id <> subscription.tenant_id;

ALTER TABLE payment_transactions
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER provider_transaction_id;

UPDATE payment_transactions
SET idempotency_key = CONCAT('legacy-', id)
WHERE idempotency_key IS NULL;

ALTER TABLE payment_transactions
    MODIFY idempotency_key VARCHAR(128) NOT NULL,
    ADD UNIQUE KEY uk_payment_tenant_idempotency (tenant_id, idempotency_key),
    ADD INDEX idx_payment_reconciliation (status, created_at, id),
    ADD CONSTRAINT fk_payment_subscription_same_tenant
        FOREIGN KEY (tenant_id, subscription_id)
        REFERENCES tenant_subscriptions (tenant_id, id);
