package vn.inventoryai.subscription.payment;

public record PaymentIntent(
        String providerTransactionId,
        String paymentUrl
) {
}
