UPDATE ingredients
SET code = CONCAT('ING-', id)
WHERE is_deleted = FALSE
  AND (code IS NULL OR TRIM(code) = '');

UPDATE ingredients
SET code = UPPER(TRIM(code))
WHERE is_deleted = FALSE;

UPDATE ingredients ingredient
JOIN (
    SELECT duplicate_codes.store_id,
           duplicate_codes.normalized_code,
           duplicate_codes.keep_id
    FROM (
        SELECT store_id,
               UPPER(TRIM(code)) AS normalized_code,
               MIN(id) AS keep_id
        FROM ingredients
        WHERE is_deleted = FALSE
        GROUP BY store_id, UPPER(TRIM(code))
        HAVING COUNT(*) > 1
    ) duplicate_codes
) duplicates
  ON duplicates.store_id = ingredient.store_id
 AND duplicates.normalized_code = UPPER(TRIM(ingredient.code))
SET ingredient.code = CONCAT(LEFT(UPPER(TRIM(ingredient.code)), 60), '-', ingredient.id)
WHERE ingredient.is_deleted = FALSE
  AND ingredient.id <> duplicates.keep_id;

ALTER TABLE ingredients
    ADD COLUMN active_code VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN is_deleted = FALSE THEN UPPER(TRIM(code))
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT uk_ingredients_store_active_code UNIQUE (store_id, active_code);

ALTER TABLE invite_tokens
    ADD COLUMN membership_id BIGINT NULL AFTER user_id;

UPDATE invite_tokens token_row
JOIN users user_row ON user_row.id = token_row.user_id
JOIN tenant_memberships membership
  ON membership.user_id = user_row.id
 AND membership.tenant_id = user_row.store_id
SET token_row.membership_id = membership.id
WHERE token_row.membership_id IS NULL;

-- Legacy tokens without a tenant membership are ambiguous and cannot be accepted safely.
-- Their outbox rows are removed by the existing ON DELETE CASCADE relation.
DELETE FROM invite_tokens
WHERE membership_id IS NULL;

ALTER TABLE invite_tokens
    MODIFY COLUMN membership_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_invite_tokens_membership
        FOREIGN KEY (membership_id) REFERENCES tenant_memberships(id) ON DELETE CASCADE,
    ADD INDEX idx_invite_tokens_membership (membership_id);
