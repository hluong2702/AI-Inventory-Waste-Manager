package vn.inventoryai.staff;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvitationEmailOutboxServiceTest {
    @Test
    void claimUsesLeaseAndIncrementsAttempt() {
        InvitationEmailOutboxRepository repository = mock(InvitationEmailOutboxRepository.class);
        InvitationEmailOutbox row = pendingRow();
        when(repository.findClaimableForUpdate(any(), any(), eq(5))).thenReturn(List.of(row));
        InvitationEmailOutboxService service = service(repository, 5);

        var claims = service.claimBatch(5, Duration.ofMinutes(30));

        assertThat(claims).hasSize(1);
        assertThat(claims.getFirst().attempt()).isEqualTo(1);
        assertThat(claims.getFirst().leaseId()).isNotBlank();
        assertThat(row.getStatus()).isEqualTo(InvitationEmailStatus.PROCESSING);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLockedAt()).isNotNull();
    }

    @Test
    void failedDeliveryIsRetriedWithoutPersistingSensitiveExceptionMessage() {
        InvitationEmailOutboxRepository repository = mock(InvitationEmailOutboxRepository.class);
        InvitationEmailOutbox row = processingRow(1, "lease-1");
        Instant previousAttempt = row.getNextAttemptAt();
        when(repository.findById(1L)).thenReturn(Optional.of(row));
        InvitationEmailOutboxService service = service(repository, 5);

        service.markFailed(1L, "lease-1", 1,
                new IllegalStateException("smtp rejected secret-token recipient@example.com"));

        assertThat(row.getStatus()).isEqualTo(InvitationEmailStatus.PENDING);
        assertThat(row.getLastErrorCode()).isEqualTo("IllegalStateException");
        assertThat(row.getLastErrorCode()).doesNotContain("secret-token", "recipient@example.com");
        assertThat(row.getNextAttemptAt()).isAfter(previousAttempt);
        assertThat(row.getLeaseId()).isNull();
    }

    @Test
    void finalFailureStopsRetrying() {
        InvitationEmailOutboxRepository repository = mock(InvitationEmailOutboxRepository.class);
        InvitationEmailOutbox row = processingRow(5, "lease-5");
        when(repository.findById(1L)).thenReturn(Optional.of(row));
        InvitationEmailOutboxService service = service(repository, 5);

        service.markFailed(1L, "lease-5", 5, new RuntimeException("mail down"));

        assertThat(row.getStatus()).isEqualTo(InvitationEmailStatus.FAILED);
        assertThat(row.getLeaseId()).isNull();
    }

    @Test
    void successfulDeliveryClearsRawInvitationUrl() {
        InvitationEmailOutboxRepository repository = mock(InvitationEmailOutboxRepository.class);
        InvitationEmailOutbox row = processingRow(1, "lease-1");
        when(repository.findById(1L)).thenReturn(Optional.of(row));
        InvitationEmailOutboxService service = service(repository, 5);

        service.markSent(1L, "lease-1");

        assertThat(row.getStatus()).isEqualTo(InvitationEmailStatus.SENT);
        assertThat(row.getInvitationUrl()).isNull();
        assertThat(row.getSentAt()).isNotNull();
        assertThat(row.getLeaseId()).isNull();
    }

    @Test
    void staleWorkerCannotOverwriteNewLease() {
        InvitationEmailOutboxRepository repository = mock(InvitationEmailOutboxRepository.class);
        InvitationEmailOutbox row = processingRow(2, "new-lease");
        when(repository.findById(1L)).thenReturn(Optional.of(row));
        InvitationEmailOutboxService service = service(repository, 5);

        service.markSent(1L, "stale-lease");

        assertThat(row.getStatus()).isEqualTo(InvitationEmailStatus.PROCESSING);
        assertThat(row.getInvitationUrl()).isNotNull();
    }

    private InvitationEmailOutboxService service(InvitationEmailOutboxRepository repository, int maxAttempts) {
        InvitationEmailOutboxService service = new InvitationEmailOutboxService(repository);
        ReflectionTestUtils.setField(service, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(service, "retryBaseSeconds", 30L);
        return service;
    }

    private InvitationEmailOutbox pendingRow() {
        InvitationEmailOutbox row = new InvitationEmailOutbox();
        row.setId(1L);
        row.setRecipientEmail("staff@coffee.vn");
        row.setStoreName("Coffee A");
        row.setInvitationUrl("https://app.test/accept?token=secret");
        row.setStatus(InvitationEmailStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(Instant.now().minusSeconds(1));
        return row;
    }

    private InvitationEmailOutbox processingRow(int attempt, String leaseId) {
        InvitationEmailOutbox row = pendingRow();
        row.setStatus(InvitationEmailStatus.PROCESSING);
        row.setAttempts(attempt);
        row.setLeaseId(leaseId);
        row.setLockedAt(Instant.now());
        return row;
    }
}
