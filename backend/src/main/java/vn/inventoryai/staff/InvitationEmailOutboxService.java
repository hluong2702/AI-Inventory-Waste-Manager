package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationEmailOutboxService {
    private final InvitationEmailOutboxRepository outboxRepository;

    @Value("${app.staff-invite.email.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.staff-invite.email.retry-base-seconds:30}")
    private long retryBaseSeconds;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(InviteToken inviteToken, String recipientEmail, String storeName, String invitationUrl) {
        InvitationEmailOutbox outbox = new InvitationEmailOutbox();
        outbox.setInviteToken(inviteToken);
        outbox.setRecipientEmail(recipientEmail);
        outbox.setStoreName(storeName);
        outbox.setInvitationUrl(invitationUrl);
        outbox.setStatus(InvitationEmailStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(Instant.now());
        outboxRepository.save(outbox);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    List<ClaimedInvitationEmail> claimBatch(int requestedBatchSize, Duration leaseDuration) {
        Instant now = Instant.now();
        int batchSize = Math.max(1, Math.min(requestedBatchSize, 25));
        outboxRepository.discardUndeliverable(now);
        List<InvitationEmailOutbox> rows = outboxRepository.findClaimableForUpdate(
                now,
                now.minus(leaseDuration),
                batchSize
        );
        List<ClaimedInvitationEmail> claims = new ArrayList<>(rows.size());
        for (InvitationEmailOutbox row : rows) {
            if (row.getAttempts() >= Math.max(maxAttempts, 1)) {
                row.setStatus(InvitationEmailStatus.FAILED);
                row.setLockedAt(null);
                row.setLeaseId(null);
                row.setLastErrorCode("DELIVERY_ATTEMPTS_EXHAUSTED");
                row.setInvitationUrl(null);
                continue;
            }

            String leaseId = UUID.randomUUID().toString();
            row.setStatus(InvitationEmailStatus.PROCESSING);
            row.setAttempts(row.getAttempts() + 1);
            row.setLockedAt(now);
            row.setLeaseId(leaseId);
            claims.add(new ClaimedInvitationEmail(
                    row.getId(),
                    leaseId,
                    row.getRecipientEmail(),
                    row.getStoreName(),
                    row.getInvitationUrl(),
                    row.getAttempts()
            ));
        }
        return claims;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markSent(Long id, String leaseId) {
        outboxRepository.findById(id).ifPresent(row -> {
            if (!ownsLease(row, leaseId)) {
                return;
            }
            row.setStatus(InvitationEmailStatus.SENT);
            row.setSentAt(Instant.now());
            row.setLockedAt(null);
            row.setLeaseId(null);
            row.setLastErrorCode(null);
            // The raw invitation token is no longer needed after delivery.
            row.setInvitationUrl(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(Long id, String leaseId, int attempt, Throwable failure) {
        outboxRepository.findById(id).ifPresent(row -> {
            if (!ownsLease(row, leaseId)) {
                return;
            }
            row.setLockedAt(null);
            row.setLeaseId(null);
            row.setLastErrorCode(safeErrorCode(failure));
            if (attempt >= Math.max(maxAttempts, 1)) {
                row.setStatus(InvitationEmailStatus.FAILED);
                row.setInvitationUrl(null);
                return;
            }

            row.setStatus(InvitationEmailStatus.PENDING);
            row.setNextAttemptAt(Instant.now().plus(retryDelay(attempt)));
        });
    }

    private boolean ownsLease(InvitationEmailOutbox row, String leaseId) {
        return row.getStatus() == InvitationEmailStatus.PROCESSING
                && leaseId != null
                && leaseId.equals(row.getLeaseId());
    }

    private Duration retryDelay(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 10));
        long baseSeconds = Math.max(retryBaseSeconds, 1);
        long delaySeconds = Math.min(baseSeconds * (1L << exponent), Duration.ofHours(1).toSeconds());
        return Duration.ofSeconds(delaySeconds);
    }

    private String safeErrorCode(Throwable failure) {
        String type = failure == null ? "UNKNOWN" : failure.getClass().getSimpleName();
        return type.length() <= 120 ? type : type.substring(0, 120);
    }

    record ClaimedInvitationEmail(
            Long id,
            String leaseId,
            String recipientEmail,
            String storeName,
            String invitationUrl,
            int attempt
    ) {
    }
}
