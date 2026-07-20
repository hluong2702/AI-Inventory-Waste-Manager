CREATE TABLE tenant_memberships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES stores(id) ON DELETE CASCADE,
    CONSTRAINT fk_tenant_memberships_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_memberships_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_tenant_memberships_role CHECK (role IN ('OWNER', 'MANAGER', 'STAFF')),
    CONSTRAINT chk_tenant_memberships_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DISABLED')),
    INDEX idx_tenant_memberships_user_status_tenant (user_id, status, tenant_id),
    INDEX idx_tenant_memberships_tenant_role_status (tenant_id, role, status)
);

INSERT INTO tenant_memberships (tenant_id, user_id, role, status, created_at, updated_at)
SELECT u.store_id, u.id, u.role, u.status, u.created_at, CURRENT_TIMESTAMP
FROM users u
WHERE u.store_id IS NOT NULL
  AND u.role IN ('OWNER', 'MANAGER', 'STAFF');

INSERT INTO tenant_memberships (tenant_id, user_id, role, status, created_at, updated_at)
SELECT s.id, s.owner_id, 'OWNER', u.status, s.created_at, CURRENT_TIMESTAMP
FROM stores s
JOIN users u ON u.id = s.owner_id
WHERE s.owner_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    role = 'OWNER',
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP;
