CREATE TABLE stores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(180) NOT NULL,
    address VARCHAR(255),
    subscription_plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL UNIQUE,
    plan VARCHAR(20) NOT NULL,
    max_staff INT NULL,
    max_ingredients INT NULL,
    expires_at DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscriptions_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NULL,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_users_store_role (store_id, role)
);

CREATE TABLE invite_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_invite_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE ingredients (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    name VARCHAR(180) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    min_stock DECIMAL(14,3) NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ingredients_store FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_ingredients_store_deleted (store_id, is_deleted)
);

CREATE TABLE inventory_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    expiry_date DATE NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_batches_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_inventory_batches_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id),
    INDEX idx_batches_store_ingredient_expiry (store_id, ingredient_id, expiry_date)
);

CREATE TABLE stock_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    type VARCHAR(10) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_transactions_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_stock_transactions_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id),
    CONSTRAINT fk_stock_transactions_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(id),
    CONSTRAINT fk_stock_transactions_user FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_tx_store_ingredient_type_created (store_id, ingredient_id, type, created_at)
);

CREATE TABLE waste_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    reason VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_waste_records_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_waste_records_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id),
    INDEX idx_waste_store_created (store_id, created_at)
);

CREATE TABLE alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    ingredient_id BIGINT NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alerts_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_alerts_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id),
    INDEX idx_alerts_unique_open (store_id, type, ingredient_id, resolved)
);
