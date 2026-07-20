package vn.inventoryai.staff;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "invitation_email_outbox")
class InvitationEmailOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_token_id", nullable = false, unique = true)
    private InviteToken inviteToken;

    @Column(name = "recipient_email", nullable = false, length = 180)
    private String recipientEmail;

    @Column(name = "store_name", nullable = false, length = 180)
    private String storeName;

    @Column(name = "invitation_url", length = 1000)
    private String invitationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationEmailStatus status = InvitationEmailStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lease_id", length = 36)
    private String leaseId;

    @Column(name = "last_error_code", length = 120)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;
}
