package vn.inventoryai.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.store.dto.StoreRequest;
import vn.inventoryai.subscription.BillingCycle;
import vn.inventoryai.subscription.SubscriptionPlanEntity;
import vn.inventoryai.subscription.SubscriptionPlanRepository;
import vn.inventoryai.subscription.SubscriptionStatus;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {
    @Mock StoreRepository storeRepository;
    @Mock UserRepository userRepository;
    @Mock PlanEntitlementService entitlementService;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock TenantMembershipRepository membershipRepository;

    @AfterEach
    void clearSecurityContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void creationLocksOwnerAndSelectedSubscriptionBeforeCheckingQuota() {
        authenticateOwner(10L, 1L);
        AppUser owner = new AppUser();
        owner.setId(10L);
        TenantSubscription selectedSubscription = new TenantSubscription();
        selectedSubscription.setPlan(plan(SubscriptionPlan.PRO));
        SubscriptionPlanEntity freePlan = plan(SubscriptionPlan.FREE);

        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(tenantSubscriptionRepository.findActiveForUpdate(1L))
                .thenReturn(Optional.of(selectedSubscription));
        when(entitlementService.limits(SubscriptionPlan.PRO)).thenReturn(new PlanLimits(3, null, null));
        when(storeRepository.countByOwnerIdAndStatus(10L, vn.inventoryai.common.enums.StoreStatus.ACTIVE))
                .thenReturn(1L);
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(2L);
            return store;
        });
        when(planRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)).thenReturn(Optional.of(freePlan));
        when(tenantSubscriptionRepository.save(any(TenantSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StoreService service = service();

        var response = service.create(new StoreRequest("Branch 2", "District 1", "0900000000"));

        assertThat(response.id()).isEqualTo(2L);
        InOrder locks = inOrder(userRepository, tenantSubscriptionRepository, storeRepository);
        locks.verify(userRepository).findByIdForUpdate(10L);
        locks.verify(tenantSubscriptionRepository).findActiveForUpdate(1L);
        locks.verify(storeRepository).countByOwnerIdAndStatus(
                10L,
                vn.inventoryai.common.enums.StoreStatus.ACTIVE
        );
    }

    @Test
    void ownerCanUpdateAnotherOwnedStoreWithoutChangingActiveTenant() {
        authenticateOwner(10L, 1L);
        Store target = store(2L, "Branch 2");
        when(membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                10L, 2L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.of(ownerMembership(target)));
        when(storeRepository.save(target)).thenReturn(target);

        var response = service().update(2L, new StoreRequest("Updated Branch", "District 3", "0900000001"));

        assertThat(response.name()).isEqualTo("Updated Branch");
        assertThat(response.address()).isEqualTo("District 3");
        verify(storeRepository).save(target);
    }

    @Test
    void ownerCanSuspendAnotherOwnedStore() {
        authenticateOwner(10L, 1L);
        Store target = store(2L, "Branch 2");
        when(membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                10L, 2L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.of(ownerMembership(target)));

        service().delete(2L);

        assertThat(target.getStatus()).isEqualTo(StoreStatus.SUSPENDED);
        verify(storeRepository).save(target);
    }

    @Test
    void ownerCannotSuspendCurrentlySelectedStore() {
        authenticateOwner(10L, 1L);
        Store target = store(1L, "Current Branch");
        when(membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                10L, 1L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.of(ownerMembership(target)));

        assertThatThrownBy(() -> service().delete(1L))
                .isInstanceOf(AppException.class)
                .hasMessage("Cannot delete current active store");

        verify(storeRepository, never()).save(any(Store.class));
    }

    private StoreService service() {
        return new StoreService(
                storeRepository,
                userRepository,
                entitlementService,
                planRepository,
                tenantSubscriptionRepository,
                membershipRepository,
                Clock.systemUTC()
        );
    }

    private Store store(Long id, String name) {
        Store store = new Store();
        store.setId(id);
        store.setName(name);
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);
        return store;
    }

    private TenantMembership ownerMembership(Store store) {
        TenantMembership membership = new TenantMembership();
        membership.setStore(store);
        membership.setRole(Role.OWNER);
        membership.setStatus(UserStatus.ACTIVE);
        return membership;
    }

    private void authenticateOwner(Long userId, Long storeId) {
        UserPrincipal principal = new UserPrincipal(userId, storeId, "owner@example.com", Role.OWNER, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        TenantContext.setStoreId(storeId);
    }

    private SubscriptionPlanEntity plan(SubscriptionPlan code) {
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        plan.setCode(code);
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setFeatureLimits("{\"features\":[]}");
        return plan;
    }
}
