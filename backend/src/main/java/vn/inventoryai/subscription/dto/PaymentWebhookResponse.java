package vn.inventoryai.subscription.dto;

import vn.inventoryai.subscription.PaymentStatus;

public record PaymentWebhookResponse(
        Long paymentTransactionId,
        PaymentStatus status,
        boolean changed
) {
}
