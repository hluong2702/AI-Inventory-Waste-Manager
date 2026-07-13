package vn.inventoryai.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.billing.dto.BillingEntitlementsResponse;
import vn.inventoryai.billing.dto.ChangePlanRequest;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.store.Subscription;
import vn.inventoryai.store.SubscriptionRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingService {
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final PlanEntitlementService planEntitlementService;

    @Transactional(readOnly = true)
    public BillingEntitlementsResponse entitlements() {
        Long storeId = currentStoreId();
        Subscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        return toResponse(storeId, subscription);
    }

    @Transactional
    public BillingEntitlementsResponse changePlan(ChangePlanRequest request) {
        Long storeId = currentStoreId();
        Subscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));

        BillingUsage usage = usage(storeId);
        PlanLimits targetLimits = planEntitlementService.limits(request.plan());
        if (targetLimits.staff() != null && usage.staff() > targetLimits.staff()) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Không thể hạ gói vì số nhân sự hiện tại vượt giới hạn gói mới.");
        }
        if (targetLimits.ingredients() != null && usage.ingredients() > targetLimits.ingredients()) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Không thể hạ gói vì số nguyên liệu hiện tại vượt giới hạn gói mới.");
        }

        subscription.setPlan(request.plan());
        subscription.setMaxStaff(targetLimits.staff());
        subscription.setMaxIngredients(targetLimits.ingredients());
        subscription.setActive(true);
        subscription.setExpiresAt(request.plan() == SubscriptionPlan.FREE ? LocalDate.now().plusMonths(1) : LocalDate.now().plusMonths(1));
        subscriptionRepository.save(subscription);
        return toResponse(storeId, subscription);
    }

    private Long currentStoreId() {
        Long storeId = SecurityUtils.storeId();
        if (storeId == null) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "System admin must select a store before using billing");
        }
        return storeId;
    }

    private BillingEntitlementsResponse toResponse(Long storeId, Subscription subscription) {
        PlanDefinition definition = planEntitlementService.definition(subscription.getPlan());
        return new BillingEntitlementsResponse(
                subscription.getPlan(),
                subscription.getExpiresAt(),
                subscription.isActive(),
                definition.limits(),
                usage(storeId),
                definition.features(),
                planEntitlementService.allPlans()
        );
    }

    private BillingUsage usage(Long storeId) {
        long staff = userRepository.countByStoreIdAndRoleInAndStatusNot(storeId, List.of(Role.MANAGER, Role.STAFF), UserStatus.DISABLED);
        long ingredients = ingredientRepository.countByStoreIdAndDeletedFalse(storeId);
        return new BillingUsage(1, staff, ingredients);
    }
}
