package vn.inventoryai.subscription.payment;

import vn.inventoryai.subscription.PaymentStatus;
import vn.inventoryai.subscription.PaymentTransaction;
import vn.inventoryai.subscription.dto.PaymentWebhookRequest;

public interface PaymentGatewayService {
    String provider();

    PaymentIntent createPayment(PaymentTransaction transaction);

    boolean verifyWebhook(PaymentWebhookRequest request);

    PaymentStatus queryStatus(PaymentTransaction transaction);
}
