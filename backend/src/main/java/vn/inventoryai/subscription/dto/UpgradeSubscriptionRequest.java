package vn.inventoryai.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.inventoryai.common.enums.SubscriptionPlan;

public record UpgradeSubscriptionRequest(
        @NotNull SubscriptionPlan targetPlan,
        @NotBlank String paymentProvider,
        @NotBlank String paymentMethod
) {
}
