package vn.inventoryai.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.inventoryai.subscription.PaymentStatus;

public record PaymentWebhookRequest(
        @NotBlank String providerTransactionId,
        @NotNull PaymentStatus status,
        String signature,
        String failureReason
) {
}
