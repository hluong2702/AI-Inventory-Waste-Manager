package vn.inventoryai.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.billing.dto.BillingEntitlementsResponse;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingService {
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantMembershipRepository membershipRepository;
    private final IngredientRepository ingredientRepository;
    private final PlanEntitlementService planEntitlementService;

    @Transactional(readOnly = true)
    public BillingEntitlementsResponse entitlements() {
        Long storeId = currentStoreId();
        TenantSubscription subscription = tenantSubscriptionRepository.findActive(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        return toResponse(storeId, subscription);
    }

    private Long currentStoreId() {
        Long storeId = SecurityUtils.storeId();
        if (storeId == null) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "System admin must select a store before using billing");
        }
        return storeId;
    }

    private BillingEntitlementsResponse toResponse(Long storeId, TenantSubscription subscription) {
        PlanDefinition definition = planEntitlementService.definition(subscription.getPlan().getCode());
        return new BillingEntitlementsResponse(
                subscription.getPlan().getCode(),
                subscription.getEndDate(),
                subscription.getStatus() == vn.inventoryai.subscription.SubscriptionStatus.ACTIVE,
                definition.limits(),
                usage(storeId),
                definition.features(),
                planEntitlementService.allPlans()
        );
    }

    private BillingUsage usage(Long storeId) {
        long staff = membershipRepository.countByStoreIdAndRoleInAndStatusNot(
                storeId,
                List.of(Role.MANAGER, Role.STAFF),
                UserStatus.DISABLED
        );
        long ingredients = ingredientRepository.countByStoreIdAndDeletedFalse(storeId);
        return new BillingUsage(1, staff, ingredients);
    }
}
