CREATE TABLE tenant_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    expiry_warning_days INT NOT NULL DEFAULT 3,
    expiry_consumption_lookback_days INT NOT NULL DEFAULT 28,
    reorder_consumption_lookback_days INT NOT NULL DEFAULT 14,
    reorder_safety_buffer_days DECIMAL(6,2) NOT NULL DEFAULT 0.50,
    reorder_review_period_days DECIMAL(6,2) NOT NULL DEFAULT 7.00,
    anomaly_threshold_percent DECIMAL(6,2) NOT NULL DEFAULT 25.00,
    anomaly_min_absolute_quantity DECIMAL(14,3) NOT NULL DEFAULT 1.000,
    daily_action_display_limit INT NOT NULL DEFAULT 10,
    daily_action_refresh_cron VARCHAR(80) NOT NULL DEFAULT '0 0 * * * *',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id) REFERENCES stores(id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_settings_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_tenant_settings_expiry_days CHECK (expiry_warning_days > 0),
    CONSTRAINT chk_tenant_settings_expiry_lookback CHECK (expiry_consumption_lookback_days > 0),
    CONSTRAINT chk_tenant_settings_reorder_lookback CHECK (reorder_consumption_lookback_days > 0),
    CONSTRAINT chk_tenant_settings_safety_buffer CHECK (reorder_safety_buffer_days >= 0),
    CONSTRAINT chk_tenant_settings_review_period CHECK (reorder_review_period_days > 0),
    CONSTRAINT chk_tenant_settings_anomaly_threshold CHECK (anomaly_threshold_percent > 0),
    CONSTRAINT chk_tenant_settings_anomaly_min_qty CHECK (anomaly_min_absolute_quantity >= 0),
    CONSTRAINT chk_tenant_settings_display_limit CHECK (daily_action_display_limit BETWEEN 1 AND 100)
);

INSERT INTO tenant_settings (tenant_id)
SELECT id FROM stores;

CREATE TABLE daily_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    product_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    risk_qty_min DECIMAL(14,3) NULL,
    risk_qty_max DECIMAL(14,3) NULL,
    risk_value_estimate DECIMAL(16,2) NOT NULL DEFAULT 0,
    priority_score DECIMAL(20,6) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    metadata JSON NOT NULL,
    acknowledged_at TIMESTAMP NULL,
    resolved_at TIMESTAMP NULL,
    dismissed_at TIMESTAMP NULL,
    dismiss_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_daily_actions_tenant FOREIGN KEY (tenant_id) REFERENCES stores(id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_actions_product FOREIGN KEY (product_id) REFERENCES ingredients(id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_actions_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id) ON DELETE CASCADE,
    CONSTRAINT chk_daily_actions_type CHECK (action_type IN ('EXPIRY_RISK', 'REORDER', 'ANOMALY')),
    CONSTRAINT chk_daily_actions_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT chk_daily_actions_risk_qty CHECK (
        (risk_qty_min IS NULL AND risk_qty_max IS NULL)
        OR (risk_qty_min >= 0 AND risk_qty_max >= risk_qty_min)
    ),
    CONSTRAINT chk_daily_actions_risk_value CHECK (risk_value_estimate >= 0),
    CONSTRAINT chk_daily_actions_priority CHECK (priority_score >= 0),
    INDEX idx_daily_actions_tenant_status_priority (tenant_id, status, priority_score DESC),
    INDEX idx_daily_actions_recompute_key (tenant_id, action_type, product_id, batch_id, status),
    INDEX idx_daily_actions_tenant_expiry (tenant_id, expires_at),
    INDEX idx_daily_actions_computed_at (computed_at)
);
