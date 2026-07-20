package vn.inventoryai.subscription.payment;

import vn.inventoryai.subscription.PaymentStatus;

public record PaymentIntent(
        String providerTransactionId,
        String paymentUrl,
        PaymentStatus status
) {
    public PaymentIntent(String providerTransactionId, String paymentUrl) {
        this(providerTransactionId, paymentUrl, PaymentStatus.PENDING);
    }
}
