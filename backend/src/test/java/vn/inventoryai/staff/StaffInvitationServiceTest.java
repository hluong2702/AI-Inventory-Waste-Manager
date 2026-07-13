package vn.inventoryai.staff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.StoreAccessService;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.staff.dto.AcceptInvitationRequest;
import vn.inventoryai.staff.dto.InviteStaffRequest;
import vn.inventoryai.staff.dto.InvitationStatus;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.store.SubscriptionRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffInvitationServiceTest {
    @Mock
    StoreAccessService storeAccessService;
    @Mock
    StoreRepository storeRepository;
    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    InviteTokenRepository inviteTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EmailService emailService;
    @Mock
    PlanEntitlementService planEntitlementService;
    @Mock
    StaffInviteRateLimiter staffInviteRateLimiter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptInvitationRejectsExpiredToken() {
        StaffInvitationService service = service();
        when(inviteTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(inviteToken(false, Instant.now().minusSeconds(1))));

        assertThatThrownBy(() -> service.acceptInvitation(new AcceptInvitationRequest("expired-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
                    assertThat(ex.getMessage()).contains("expired");
                });
    }

    @Test
    void acceptInvitationRejectsReusedToken() {
        StaffInvitationService service = service();
        when(inviteTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(inviteToken(true, Instant.now().plusSeconds(3600))));

        assertThatThrownBy(() -> service.acceptInvitation(new AcceptInvitationRequest("used-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
                    assertThat(ex.getMessage()).contains("already been used");
                });
    }

    @Test
    void acceptInvitationRejectsInvalidToken() {
        StaffInvitationService service = service();
        when(inviteTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation(new AcceptInvitationRequest("missing-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    @Test
    void verifyInvitationReturnsExpiredStatus() {
        StaffInvitationService service = service();
        when(inviteTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(inviteToken(false, Instant.now().minusSeconds(1))));

        assertThat(service.verifyInvitation("expired-token").status()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void managerCannotInviteManager() {
        authenticate(new UserPrincipal(21L, 10L, "manager@coffee.vn", Role.MANAGER, false));
        StaffInvitationService service = service();

        assertThatThrownBy(() -> service.invite(10L, new InviteStaffRequest("new-manager@coffee.vn", Role.MANAGER)))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(staffInviteRateLimiter);
    }

    private StaffInvitationService service() {
        StaffInvitationService service = new StaffInvitationService(
                storeAccessService,
                storeRepository,
                subscriptionRepository,
                userRepository,
                inviteTokenRepository,
                passwordEncoder,
                emailService,
                planEntitlementService,
                staffInviteRateLimiter
        );
        ReflectionTestUtils.setField(service, "inviteUrl", "http://localhost:5173/accept-invite");
        return service;
    }

    private InviteToken inviteToken(boolean used, Instant expiresAt) {
        Store store = new Store();
        store.setId(10L);
        store.setName("Coffee A");
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);

        AppUser user = new AppUser();
        user.setId(30L);
        user.setStore(store);
        user.setFullName("Pending Staff");
        user.setEmail("pending@coffee.vn");
        user.setPasswordHash("placeholder");
        user.setRole(Role.STAFF);
        user.setStatus(UserStatus.PENDING_ACTIVATION);

        InviteToken inviteToken = new InviteToken();
        inviteToken.setId(40L);
        inviteToken.setUser(user);
        inviteToken.setTokenHash("hashed");
        inviteToken.setExpiresAt(expiresAt);
        inviteToken.setUsed(used);
        return inviteToken;
    }

    private void authenticate(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
