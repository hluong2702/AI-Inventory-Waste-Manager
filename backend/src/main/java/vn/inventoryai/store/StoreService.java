package vn.inventoryai.store;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.store.dto.StoreRequest;
import vn.inventoryai.store.dto.StoreResponse;
import vn.inventoryai.subscription.SubscriptionPlanRepository;
import vn.inventoryai.subscription.SubscriptionStatus;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final PlanEntitlementService planEntitlementService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantMembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StoreResponse> currentUserStores() {
        var principal = SecurityUtils.principal();
        if (principal.role() == Role.SYSTEM_ADMIN) {
            throw new AppException(
                    ErrorCode.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "System administrators must use the audited admin store endpoint"
            );
        }
        return membershipRepository.findAllByUserIdAndStatusAndStoreStatusOrderByIdAsc(
                        principal.userId(), UserStatus.ACTIVE, StoreStatus.ACTIVE
                ).stream()
                .map(TenantMembership::getStore)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StoreResponse create(StoreRequest request) {
        var principal = SecurityUtils.principal();
        // Serialize all store creation by the same owner. Locking only the selected
        // subscription would still allow concurrent requests through two owned tenants.
        var owner = userRepository.findByIdForUpdate(principal.userId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
        TenantSubscription currentSubscription = tenantSubscriptionRepository.findActiveForUpdate(SecurityUtils.storeId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        Integer maxStores = planEntitlementService.limits(currentSubscription.getPlan().getCode()).stores();
        long currentStores = storeRepository.countByOwnerIdAndStatus(principal.userId(), StoreStatus.ACTIVE);
        if (maxStores != null && currentStores >= maxStores) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Store limit exceeded for current plan");
        }

        Store store = new Store();
        store.setName(request.name());
        store.setAddress(request.address());
        store.setPhone(request.phone());
        store.setOwner(owner);
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);
        Store saved = storeRepository.save(store);

        LocalDate businessDate = LocalDate.now(clock);
        var plan = subscriptionPlanRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription plan not found"));
        TenantSubscription tenantSubscription = new TenantSubscription();
        tenantSubscription.setTenant(saved);
        tenantSubscription.setPlan(plan);
        tenantSubscription.setStatus(SubscriptionStatus.ACTIVE);
        tenantSubscription.setStartDate(businessDate);
        tenantSubscription.setEndDate(businessDate.plusMonths(1));
        tenantSubscription.setAutoRenew(false);
        tenantSubscription.setActivatedAt(Instant.now());
        tenantSubscriptionRepository.save(tenantSubscription);

        TenantMembership membership = new TenantMembership();
        membership.setStore(saved);
        membership.setUser(owner);
        membership.setRole(Role.OWNER);
        membership.setStatus(UserStatus.ACTIVE);
        membershipRepository.save(membership);

        return toResponse(saved);
    }

    @Transactional
    public StoreResponse update(Long id, StoreRequest request) {
        Store store = ownedStore(id);
        store.setName(request.name());
        store.setAddress(request.address());
        store.setPhone(request.phone());
        return toResponse(storeRepository.save(store));
    }

    @Transactional
    public void delete(Long id) {
        Store store = ownedStore(id);
        if (SecurityUtils.storeId() != null && SecurityUtils.storeId().equals(id)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Cannot delete current active store");
        }
        store.setStatus(StoreStatus.SUSPENDED);
        storeRepository.save(store);
    }

    private Store ownedStore(Long id) {
        Long userId = SecurityUtils.principal().userId();
        TenantMembership membership = membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                        userId, id, UserStatus.ACTIVE, StoreStatus.ACTIVE
                )
                .filter(candidate -> candidate.getRole() == Role.OWNER)
                .orElseThrow(() -> new AppException(
                        ErrorCode.STORE_MISMATCH,
                        HttpStatus.FORBIDDEN,
                        "Store does not belong to current owner"
                ));
        return membership.getStore();
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getPhone(), store.getSubscriptionPlan(), store.getStatus());
    }
}
