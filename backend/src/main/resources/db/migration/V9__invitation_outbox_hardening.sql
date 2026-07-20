CREATE TABLE invitation_email_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invite_token_id BIGINT NOT NULL,
    recipient_email VARCHAR(180) NOT NULL,
    store_name VARCHAR(180) NOT NULL,
    invitation_url VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_at TIMESTAMP(6) NULL,
    lease_id VARCHAR(36) NULL,
    last_error_code VARCHAR(120) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_invitation_email_outbox_token
        FOREIGN KEY (invite_token_id) REFERENCES invite_tokens(id) ON DELETE CASCADE,
    CONSTRAINT chk_invitation_email_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT chk_invitation_email_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_invitation_email_payload
        CHECK (status IN ('SENT', 'FAILED') OR invitation_url IS NOT NULL),
    CONSTRAINT uk_invitation_email_outbox_token UNIQUE (invite_token_id),
    INDEX idx_invitation_email_delivery (status, next_attempt_at, locked_at, id)
);
