package vn.inventoryai.staff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.billing.PlanLimits;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.StoreAccessService;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.staff.dto.AcceptInvitationRequest;
import vn.inventoryai.staff.dto.InvitationStatus;
import vn.inventoryai.staff.dto.InviteStaffRequest;
import vn.inventoryai.store.Store;
import vn.inventoryai.subscription.SubscriptionPlanEntity;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffInvitationServiceTest {
    @Mock
    StoreAccessService storeAccessService;
    @Mock
    UserRepository userRepository;
    @Mock
    TenantMembershipRepository tenantMembershipRepository;
    @Mock
    TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock
    InviteTokenRepository inviteTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    InvitationEmailOutboxService emailOutboxService;
    @Mock
    StaffInviteRateLimiter staffInviteRateLimiter;
    @Mock
    PlanEntitlementService planEntitlementService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptInvitationRejectsExpiredTokenUnderWriteLock() {
        when(inviteTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(inviteToken(false, Instant.now().minusSeconds(1), UserStatus.PENDING_ACTIVATION, Role.STAFF)));

        assertThatThrownBy(() -> service().acceptInvitation(
                new AcceptInvitationRequest("expired-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
                    assertThat(ex.getMessage()).contains("expired");
                });
    }

    @Test
    void acceptInvitationRejectsReusedTokenUnderWriteLock() {
        when(inviteTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(inviteToken(true, Instant.now().plusSeconds(3600), UserStatus.ACTIVE, Role.STAFF)));

        assertThatThrownBy(() -> service().acceptInvitation(
                new AcceptInvitationRequest("used-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
                    assertThat(ex.getMessage()).contains("already been used");
                });
    }

    @Test
    void acceptInvitationRejectsDisabledPendingUserEvenWhenTokenIsUnused() {
        when(inviteTokenRepository.findByTokenHashForUpdate(any()))
                .thenReturn(Optional.of(inviteToken(false, Instant.now().plusSeconds(3600), UserStatus.DISABLED, Role.STAFF)));

        assertThatThrownBy(() -> service().acceptInvitation(
                new AcceptInvitationRequest("disabled-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void acceptInvitationAtomicallyActivatesUserAndMembership() {
        InviteToken token = inviteToken(false, Instant.now().plusSeconds(3600), UserStatus.PENDING_ACTIVATION, Role.STAFF);
        TenantMembership membership = token.getMembership();
        when(inviteTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");

        assertThat(service().acceptInvitation(
                new AcceptInvitationRequest("valid-token", " New Staff ", "password123")).valid()).isTrue();

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(token.getUser().getFullName()).isEqualTo("New Staff");
        assertThat(membership.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(inviteTokenRepository).save(token);
        verify(tenantMembershipRepository).save(membership);
    }

    @Test
    void acceptInvitationRejectsInvalidToken() {
        when(inviteTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().acceptInvitation(
                new AcceptInvitationRequest("missing-token", "New Staff", "password123")))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    @Test
    void verifyInvitationReturnsExpiredStatus() {
        when(inviteTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(inviteToken(false, Instant.now().minusSeconds(1), UserStatus.PENDING_ACTIVATION, Role.STAFF)));

        assertThat(service().verifyInvitation("expired-token").status()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void managerCannotInviteOrRevokeManager() {
        authenticate(new UserPrincipal(21L, 10L, "manager@coffee.vn", Role.MANAGER, false));

        assertThatThrownBy(() -> service().invite(10L,
                new InviteStaffRequest("new-manager@coffee.vn", Role.MANAGER)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        AppUser pendingManager = user(30L, Role.MANAGER, UserStatus.PENDING_ACTIVATION);
        when(tenantMembershipRepository.findByUserIdAndStoreIdForUpdate(30L, 10L))
                .thenReturn(Optional.of(membership(pendingManager, UserStatus.PENDING_ACTIVATION)));
        assertThatThrownBy(() -> service().revokeInvitation(10L, 30L))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(staffInviteRateLimiter);
        verify(inviteTokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    void managerSeatsCountTowardPlanLimitWhileSubscriptionRowIsLocked() {
        authenticate(new UserPrincipal(1L, 10L, "owner@coffee.vn", Role.OWNER, false));
        when(tenantSubscriptionRepository.findActiveForUpdate(10L))
                .thenReturn(Optional.of(subscription(SubscriptionPlan.FREE)));
        when(planEntitlementService.limits(SubscriptionPlan.FREE)).thenReturn(new PlanLimits(1, 2, 30));
        when(tenantMembershipRepository.countByStoreIdAndRoleInAndStatusNot(eq(10L), anyList(), eq(UserStatus.DISABLED)))
                .thenReturn(2L);

        assertThatThrownBy(() -> service().invite(10L,
                new InviteStaffRequest("new-manager@coffee.vn", Role.MANAGER)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Role>> roles = ArgumentCaptor.forClass(List.class);
        verify(tenantMembershipRepository).countByStoreIdAndRoleInAndStatusNot(eq(10L), roles.capture(), eq(UserStatus.DISABLED));
        assertThat(roles.getValue()).containsExactlyInAnyOrder(Role.MANAGER, Role.STAFF);
        verifyNoInteractions(staffInviteRateLimiter, emailOutboxService);
    }

    @Test
    void validInvitationCreatesPendingMembershipAndTransactionalOutboxEntry() {
        authenticate(new UserPrincipal(1L, 10L, "owner@coffee.vn", Role.OWNER, false));
        TenantSubscription subscription = subscription(SubscriptionPlan.FREE);
        when(userRepository.findByEmail("staff@coffee.vn")).thenReturn(Optional.empty());
        when(tenantSubscriptionRepository.findActiveForUpdate(10L)).thenReturn(Optional.of(subscription));
        when(planEntitlementService.limits(SubscriptionPlan.FREE)).thenReturn(new PlanLimits(1, 2, 30));
        when(tenantMembershipRepository.countByStoreIdAndRoleInAndStatusNot(eq(10L), anyList(), eq(UserStatus.DISABLED)))
                .thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("placeholder-hash");
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(30L);
            return user;
        });
        when(inviteTokenRepository.save(any())).thenAnswer(invocation -> {
            InviteToken token = invocation.getArgument(0);
            token.setId(40L);
            return token;
        });
        when(tenantMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            membership.setId(50L);
            return membership;
        });
        when(tenantMembershipRepository.findAllByStoreIdAndRoleInOrderByUserFullNameAsc(eq(10L), anyList()))
                .thenReturn(List.of());

        service().invite(10L, new InviteStaffRequest(" Staff@Coffee.vn ", Role.STAFF));

        verify(staffInviteRateLimiter).check(1L, 10L, "staff@coffee.vn");
        ArgumentCaptor<TenantMembership> membership = ArgumentCaptor.forClass(TenantMembership.class);
        verify(tenantMembershipRepository).saveAndFlush(membership.capture());
        assertThat(membership.getValue().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(membership.getValue().getRole()).isEqualTo(Role.STAFF);
        verify(emailOutboxService).enqueue(any(InviteToken.class), eq("staff@coffee.vn"),
                eq("Coffee A"), startsWith("http://localhost:5173/accept-invite#token="));
    }

    @Test
    void invitationCanAddAnExistingIdentityToAnotherTenant() {
        authenticate(new UserPrincipal(1L, 10L, "owner@coffee.vn", Role.OWNER, false));
        AppUser existing = user(30L, Role.STAFF, UserStatus.ACTIVE);
        existing.setStore(otherStore());
        existing.setPasswordHash("existing-password-hash");
        when(tenantSubscriptionRepository.findActiveForUpdate(10L))
                .thenReturn(Optional.of(subscription(SubscriptionPlan.PRO)));
        when(planEntitlementService.limits(SubscriptionPlan.PRO)).thenReturn(new PlanLimits(null, null, null));
        when(userRepository.findByEmail("existing@coffee.vn")).thenReturn(Optional.of(existing));
        when(tenantMembershipRepository.findByUserIdAndStoreId(30L, 10L)).thenReturn(Optional.empty());
        when(tenantMembershipRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TenantMembership membership = invocation.getArgument(0);
            membership.setId(51L);
            return membership;
        });
        when(inviteTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantMembershipRepository.findAllByStoreIdAndRoleInOrderByUserFullNameAsc(eq(10L), anyList()))
                .thenReturn(List.of());

        service().invite(10L, new InviteStaffRequest("existing@coffee.vn", Role.STAFF));

        ArgumentCaptor<TenantMembership> membership = ArgumentCaptor.forClass(TenantMembership.class);
        verify(tenantMembershipRepository).saveAndFlush(membership.capture());
        assertThat(membership.getValue().getStore().getId()).isEqualTo(10L);
        assertThat(membership.getValue().getUser()).isSameAs(existing);
        assertThat(existing.getPasswordHash()).isEqualTo("existing-password-hash");
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void acceptingInvitationForExistingIdentityDoesNotResetPasswordOrName() {
        AppUser existing = user(30L, Role.STAFF, UserStatus.ACTIVE);
        existing.setFullName("Existing Name");
        existing.setPasswordHash("existing-password-hash");
        TenantMembership pendingMembership = membership(existing, UserStatus.PENDING_ACTIVATION);
        InviteToken token = inviteToken(false, Instant.now().plusSeconds(3600), UserStatus.ACTIVE, Role.STAFF);
        token.setUser(existing);
        token.setMembership(pendingMembership);
        when(inviteTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(token));

        var response = service().acceptInvitation(new AcceptInvitationRequest("valid-token", null, null));

        assertThat(response.accountSetupRequired()).isFalse();
        assertThat(existing.getFullName()).isEqualTo("Existing Name");
        assertThat(existing.getPasswordHash()).isEqualTo("existing-password-hash");
        assertThat(pendingMembership.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void disablingActiveMembershipDoesNotDisableGlobalIdentity() {
        authenticate(new UserPrincipal(1L, 10L, "owner@coffee.vn", Role.OWNER, false));
        AppUser activeUser = user(30L, Role.STAFF, UserStatus.ACTIVE);
        TenantMembership membership = membership(activeUser, UserStatus.ACTIVE);
        when(tenantMembershipRepository.findByUserIdAndStoreIdForUpdate(30L, 10L))
                .thenReturn(Optional.of(membership));
        when(tenantMembershipRepository.findAllByStoreIdAndRoleInOrderByUserFullNameAsc(eq(10L), anyList()))
                .thenReturn(List.of());

        service().disableStaff(10L, 30L);

        assertThat(activeUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(membership.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(tenantMembershipRepository).save(membership);
        verify(userRepository, never()).save(activeUser);
        verify(inviteTokenRepository, never()).deleteByMembershipId(anyLong());
    }

    private StaffInvitationService service() {
        StaffInvitationService service = new StaffInvitationService(
                storeAccessService,
                userRepository,
                tenantMembershipRepository,
                tenantSubscriptionRepository,
                inviteTokenRepository,
                passwordEncoder,
                emailOutboxService,
                planEntitlementService,
                staffInviteRateLimiter
        );
        ReflectionTestUtils.setField(service, "inviteUrl", "http://localhost:5173/accept-invite");
        return service;
    }

    private InviteToken inviteToken(boolean used, Instant expiresAt, UserStatus status, Role role) {
        AppUser user = user(30L, role, status);
        InviteToken inviteToken = new InviteToken();
        inviteToken.setId(40L);
        inviteToken.setUser(user);
        inviteToken.setMembership(membership(user, status));
        inviteToken.setTokenHash("hashed");
        inviteToken.setExpiresAt(expiresAt);
        inviteToken.setUsed(used);
        return inviteToken;
    }

    private AppUser user(Long id, Role role, UserStatus status) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setStore(store());
        user.setFullName("Pending Staff");
        user.setEmail("pending@coffee.vn");
        user.setPasswordHash("placeholder");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private TenantMembership membership(AppUser user, UserStatus status) {
        TenantMembership membership = new TenantMembership();
        membership.setId(50L);
        membership.setStore(user.getStore());
        membership.setUser(user);
        membership.setRole(user.getRole());
        membership.setStatus(status);
        return membership;
    }

    private TenantSubscription subscription(SubscriptionPlan plan) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(60L);
        subscription.setTenant(store());
        SubscriptionPlanEntity planEntity = new SubscriptionPlanEntity();
        planEntity.setCode(plan);
        subscription.setPlan(planEntity);
        return subscription;
    }

    private Store store() {
        Store store = new Store();
        store.setId(10L);
        store.setName("Coffee A");
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);
        return store;
    }

    private Store otherStore() {
        Store store = new Store();
        store.setId(99L);
        store.setName("Other Store");
        store.setSubscriptionPlan(SubscriptionPlan.PRO);
        store.setStatus(StoreStatus.ACTIVE);
        return store;
    }

    private void authenticate(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
