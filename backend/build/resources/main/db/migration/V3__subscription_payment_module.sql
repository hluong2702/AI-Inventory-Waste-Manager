CREATE TABLE subscription_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    price DECIMAL(14,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    billing_cycle VARCHAR(20) NOT NULL,
    feature_limits JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tenant_subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    pending_downgrade_plan_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL,
    CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES stores(id),
    CONSTRAINT fk_tenant_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    CONSTRAINT fk_tenant_subscriptions_pending_plan FOREIGN KEY (pending_downgrade_plan_id) REFERENCES subscription_plans(id),
    INDEX idx_tenant_subscriptions_tenant_status (tenant_id, status),
    INDEX idx_tenant_subscriptions_status_end_date (status, end_date)
);

CREATE TABLE payment_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    payment_method VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    provider_transaction_id VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_url VARCHAR(500) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_payment_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES stores(id),
    CONSTRAINT fk_payment_transactions_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    UNIQUE KEY uk_payment_provider_transaction (provider, provider_transaction_id),
    INDEX idx_payment_tenant_status_created (tenant_id, status, created_at)
);

INSERT INTO subscription_plans (code, name, price, currency, billing_cycle, feature_limits, is_active)
VALUES
  ('FREE', 'Free', 0, 'VND', 'MONTHLY', JSON_OBJECT('stores', 1, 'staff', 2, 'ingredients', 30, 'features', JSON_ARRAY('BASIC_ALERTS', 'BASIC_REPORTS')), TRUE),
  ('BASIC', 'Basic', 299000, 'VND', 'MONTHLY', JSON_OBJECT('stores', 1, 'staff', 10, 'ingredients', 500, 'features', JSON_ARRAY('BASIC_ALERTS', 'BASIC_REPORTS', 'BASIC_FORECAST')), TRUE),
  ('PRO', 'Pro', 699000, 'VND', 'MONTHLY', JSON_OBJECT('stores', NULL, 'staff', NULL, 'ingredients', NULL, 'features', JSON_ARRAY('BASIC_ALERTS', 'BASIC_REPORTS', 'BASIC_FORECAST', 'ADVANCED_FORECAST', 'EXPORT_REPORTS', 'MULTI_STORE')), TRUE),
  ('ENTERPRISE', 'Enterprise', 0, 'VND', 'YEARLY', JSON_OBJECT('stores', NULL, 'staff', NULL, 'ingredients', NULL, 'features', JSON_ARRAY('BASIC_ALERTS', 'BASIC_REPORTS', 'BASIC_FORECAST', 'ADVANCED_FORECAST', 'EXPORT_REPORTS', 'MULTI_STORE', 'PRIORITY_SUPPORT')), TRUE);

INSERT INTO tenant_subscriptions (tenant_id, plan_id, status, start_date, end_date, auto_renew, activated_at)
SELECT s.id, p.id, 'ACTIVE', CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 1 MONTH), FALSE, CURRENT_TIMESTAMP
FROM stores s
JOIN subscription_plans p ON p.code = s.subscription_plan;
