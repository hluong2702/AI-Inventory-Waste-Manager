package vn.inventoryai.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.inventoryai.subscription.PaymentStatus;

import java.util.Map;

public record PaymentWebhookRequest(
        @NotBlank String providerTransactionId,
        @NotNull PaymentStatus status,
        @NotBlank String signature,
        String failureReason,
        Map<String, Object> providerData
) {
    public PaymentWebhookRequest {
        providerData = providerData == null ? Map.of() : Map.copyOf(providerData);
    }
}
