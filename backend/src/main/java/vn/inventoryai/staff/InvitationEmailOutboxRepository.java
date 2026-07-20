package vn.inventoryai.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface InvitationEmailOutboxRepository extends JpaRepository<InvitationEmailOutbox, Long> {
    @Query(value = """
            SELECT outbox.*
              FROM invitation_email_outbox outbox
              JOIN invite_tokens token ON token.id = outbox.invite_token_id
              JOIN users invited_user ON invited_user.id = token.user_id
             WHERE (
                       (outbox.status = 'PENDING' AND outbox.next_attempt_at <= :now)
                    OR (outbox.status = 'PROCESSING' AND outbox.locked_at <= :staleBefore)
                   )
               AND token.used = FALSE
               AND token.expires_at > :now
               AND invited_user.status = 'PENDING_ACTIVATION'
             ORDER BY outbox.id
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InvitationEmailOutbox> findClaimableForUpdate(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query(value = """
            UPDATE invitation_email_outbox outbox
            JOIN invite_tokens token ON token.id = outbox.invite_token_id
            JOIN users invited_user ON invited_user.id = token.user_id
               SET outbox.status = 'FAILED',
                   outbox.invitation_url = NULL,
                   outbox.locked_at = NULL,
                   outbox.lease_id = NULL,
                   outbox.last_error_code = 'INVITATION_NO_LONGER_VALID'
             WHERE outbox.status IN ('PENDING', 'PROCESSING')
               AND (token.used = TRUE OR token.expires_at <= :now OR invited_user.status <> 'PENDING_ACTIVATION')
            """, nativeQuery = true)
    int discardUndeliverable(@Param("now") Instant now);
}
