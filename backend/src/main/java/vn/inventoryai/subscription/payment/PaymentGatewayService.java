package vn.inventoryai.subscription.payment;

import vn.inventoryai.subscription.PaymentStatus;
import vn.inventoryai.subscription.PaymentTransaction;
import vn.inventoryai.subscription.dto.PaymentWebhookRequest;

public interface PaymentGatewayService {
    String provider();

    /**
     * Allocates the provider reference from a locally persisted payment. The returned
     * value is committed before any provider request, so webhooks and recovery can
     * always correlate an ambiguous create call.
     */
    String reserveProviderTransactionId(PaymentTransaction transaction);

    PaymentIntent createPayment(PaymentTransaction transaction, String clientIp);

    default PaymentIntent recoverOrCreatePayment(PaymentTransaction transaction, String clientIp) {
        return createPayment(transaction, clientIp);
    }

    default boolean isDefinitiveCreationFailure(RuntimeException failure) {
        return false;
    }

    boolean verifyWebhook(PaymentWebhookRequest request);

    default boolean validateWebhookPayment(PaymentTransaction transaction, PaymentWebhookRequest request) {
        return true;
    }

    PaymentStatus queryStatus(PaymentTransaction transaction);
}
