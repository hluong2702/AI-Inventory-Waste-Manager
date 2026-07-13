package vn.inventoryai.billing;

import vn.inventoryai.common.enums.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.List;

public record PlanDefinition(
        SubscriptionPlan plan,
        BigDecimal monthlyPrice,
        PlanLimits limits,
        List<String> features
) {
}
