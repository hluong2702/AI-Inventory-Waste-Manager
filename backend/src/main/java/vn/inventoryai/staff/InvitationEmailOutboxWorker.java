package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.observability.OperationalMetrics;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.invitation-email.enabled", havingValue = "true")
@RequiredArgsConstructor
class InvitationEmailOutboxWorker {
    private final InvitationEmailOutboxService outboxService;
    private final EmailService emailService;
    private final OperationalMetrics operationalMetrics;

    @Value("${app.staff-invite.email.batch-size:5}")
    private int batchSize;

    @Value("${app.staff-invite.email.lease-minutes:30}")
    private long leaseMinutes;

    @Scheduled(
            fixedDelayString = "${app.staff-invite.email.poll-delay-ms:5000}",
            initialDelayString = "${app.staff-invite.email.initial-delay-ms:5000}",
            scheduler = "invitationEmailTaskScheduler"
    )
    void deliverPendingInvitations() {
        Duration lease = Duration.ofMinutes(Math.max(leaseMinutes, 1));
        for (InvitationEmailOutboxService.ClaimedInvitationEmail claim
                : outboxService.claimBatch(batchSize, lease)) {
            try {
                emailService.sendStaffInvitationEmail(
                        claim.recipientEmail(),
                        claim.storeName(),
                        claim.invitationUrl()
                );
            } catch (RuntimeException failure) {
                operationalMetrics.invitationEmail("delivery_failure");
                outboxService.markFailed(claim.id(), claim.leaseId(), claim.attempt(), failure);
                log.warn("Invitation email delivery failed; outboxId={}, attempt={}, errorType={}",
                        claim.id(), claim.attempt(), failure.getClass().getSimpleName());
                continue;
            }

            try {
                outboxService.markSent(claim.id(), claim.leaseId());
                operationalMetrics.invitationEmail("success");
                log.info("Invitation email delivered; outboxId={}", claim.id());
            } catch (RuntimeException persistenceFailure) {
                operationalMetrics.invitationEmail("state_update_failure");
                // Keep the lease instead of marking the message retryable immediately after SMTP accepted it.
                log.error("Invitation email delivered but outbox state update failed; outboxId={}, errorType={}",
                        claim.id(), persistenceFailure.getClass().getSimpleName());
            }
        }
    }
}
