package vn.inventoryai.staff;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.TenantMembership;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "invite_tokens")
public class InviteToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private TenantMembership membership;

    @Column(name = "token", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;
}
