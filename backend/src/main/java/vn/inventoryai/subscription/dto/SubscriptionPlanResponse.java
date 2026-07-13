package vn.inventoryai.subscription.dto;

import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.subscription.BillingCycle;

import java.math.BigDecimal;

public record SubscriptionPlanResponse(
        Long id,
        SubscriptionPlan code,
        String name,
        BigDecimal price,
        String currency,
        BillingCycle billingCycle,
        String featureLimits
) {
}
