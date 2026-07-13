package vn.inventoryai.subscription.dto;

import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.subscription.SubscriptionStatus;

import java.time.LocalDate;

public record CurrentSubscriptionResponse(
        Long subscriptionId,
        SubscriptionPlan plan,
        SubscriptionStatus status,
        LocalDate startDate,
        LocalDate endDate,
        boolean autoRenew,
        SubscriptionPlan pendingDowngradePlan,
        String featureLimits
) {
}
