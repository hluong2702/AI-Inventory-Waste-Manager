package vn.inventoryai.subscription.dto;

import vn.inventoryai.subscription.PaymentStatus;
import vn.inventoryai.subscription.SubscriptionStatus;

import java.math.BigDecimal;

public record UpgradeSubscriptionResponse(
        Long subscriptionId,
        Long paymentTransactionId,
        SubscriptionStatus subscriptionStatus,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        String currency,
        String provider,
        String providerTransactionId,
        String paymentUrl
) {
}
