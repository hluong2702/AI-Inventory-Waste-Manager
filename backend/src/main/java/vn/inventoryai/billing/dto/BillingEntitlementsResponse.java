package vn.inventoryai.billing.dto;

import vn.inventoryai.billing.BillingUsage;
import vn.inventoryai.billing.PlanDefinition;
import vn.inventoryai.billing.PlanLimits;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.time.LocalDate;
import java.util.List;

public record BillingEntitlementsResponse(
        SubscriptionPlan plan,
        LocalDate expiresAt,
        boolean active,
        PlanLimits limits,
        BillingUsage usage,
        List<String> enabledFeatures,
        List<PlanDefinition> availablePlans
) {
}
