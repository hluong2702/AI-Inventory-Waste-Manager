package vn.inventoryai.staff;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.inventoryai.common.observability.OperationalMetrics;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InvitationEmailOutboxWorkerTest {
    @Test
    void marksSuccessfulAndFailedDeliveriesWithoutLosingBatch() {
        InvitationEmailOutboxService outboxService = mock(InvitationEmailOutboxService.class);
        EmailService emailService = mock(EmailService.class);
        var first = new InvitationEmailOutboxService.ClaimedInvitationEmail(
                1L, "lease-1", "one@coffee.vn", "Coffee A", "https://app/one", 1);
        var second = new InvitationEmailOutboxService.ClaimedInvitationEmail(
                2L, "lease-2", "two@coffee.vn", "Coffee A", "https://app/two", 2);
        when(outboxService.claimBatch(eq(5), any(Duration.class))).thenReturn(List.of(first, second));
        doThrow(new EmailDeliveryException(new RuntimeException("smtp down")))
                .when(emailService).sendStaffInvitationEmail("two@coffee.vn", "Coffee A", "https://app/two");
        InvitationEmailOutboxWorker worker = new InvitationEmailOutboxWorker(
                outboxService,
                emailService,
                mock(OperationalMetrics.class)
        );
        ReflectionTestUtils.setField(worker, "batchSize", 5);
        ReflectionTestUtils.setField(worker, "leaseMinutes", 30L);

        worker.deliverPendingInvitations();

        verify(outboxService).markSent(1L, "lease-1");
        verify(outboxService).markFailed(eq(2L), eq("lease-2"), eq(2), any(EmailDeliveryException.class));
    }
}
