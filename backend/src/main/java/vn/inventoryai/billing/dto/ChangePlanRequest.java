package vn.inventoryai.billing.dto;

import jakarta.validation.constraints.NotNull;
import vn.inventoryai.common.enums.SubscriptionPlan;

public record ChangePlanRequest(
        @NotNull SubscriptionPlan plan
) {
}
