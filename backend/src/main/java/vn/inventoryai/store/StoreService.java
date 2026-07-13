package vn.inventoryai.store;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.store.dto.StoreRequest;
import vn.inventoryai.store.dto.StoreResponse;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanEntitlementService planEntitlementService;

    @Transactional(readOnly = true)
    public List<StoreResponse> currentUserStores() {
        var principal = SecurityUtils.principal();
        if (principal.role() == Role.SYSTEM_ADMIN) {
            return storeRepository.findAll().stream().map(this::toResponse).toList();
        }
        var user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
        if (principal.role() == Role.OWNER) {
            return storeRepository.findByOwnerId(principal.userId()).stream()
                    .filter(store -> store.getStatus() == StoreStatus.ACTIVE)
                    .map(this::toResponse)
                    .toList();
        }
        Store store = user.getStore();
        if (store == null) return List.of();
        return List.of(toResponse(store));
    }

    @Transactional
    public StoreResponse create(StoreRequest request) {
        var principal = SecurityUtils.principal();
        var owner = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
        Subscription currentSubscription = subscriptionRepository.findByStoreId(SecurityUtils.storeId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        Integer maxStores = planEntitlementService.limits(currentSubscription.getPlan()).stores();
        long currentStores = storeRepository.countByOwnerIdAndStatus(principal.userId(), StoreStatus.ACTIVE);
        if (maxStores != null && currentStores >= maxStores) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Store limit exceeded for current plan");
        }

        Store store = new Store();
        store.setName(request.name());
        store.setAddress(request.address());
        store.setPhone(request.phone());
        store.setOwner(owner);
        store.setSubscriptionPlan(currentSubscription.getPlan());
        store.setStatus(StoreStatus.ACTIVE);
        Store saved = storeRepository.save(store);

        Subscription subscription = new Subscription();
        subscription.setStore(saved);
        subscription.setPlan(currentSubscription.getPlan());
        subscription.setMaxStaff(planEntitlementService.limits(currentSubscription.getPlan()).staff());
        subscription.setMaxIngredients(planEntitlementService.limits(currentSubscription.getPlan()).ingredients());
        subscription.setExpiresAt(currentSubscription.getExpiresAt() == null ? LocalDate.now().plusMonths(1) : currentSubscription.getExpiresAt());
        subscription.setActive(true);
        subscriptionRepository.save(subscription);

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
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        Long userId = SecurityUtils.principal().userId();
        if (store.getOwner() == null || !store.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "Store does not belong to current owner");
        }
        return store;
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(store.getId(), store.getName(), store.getAddress(), store.getPhone(), store.getSubscriptionPlan(), store.getStatus());
    }
}
