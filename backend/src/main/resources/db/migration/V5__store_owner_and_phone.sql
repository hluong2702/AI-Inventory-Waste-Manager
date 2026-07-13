ALTER TABLE stores ADD COLUMN phone VARCHAR(40);
ALTER TABLE stores ADD COLUMN owner_id BIGINT;

UPDATE stores s
SET owner_id = (
    SELECT u.id
    FROM users u
    WHERE u.store_id = s.id AND u.role = 'OWNER'
    ORDER BY u.id
    LIMIT 1
);

ALTER TABLE stores ADD CONSTRAINT fk_stores_owner FOREIGN KEY (owner_id) REFERENCES users(id);
CREATE INDEX idx_stores_owner_status ON stores(owner_id, status);
