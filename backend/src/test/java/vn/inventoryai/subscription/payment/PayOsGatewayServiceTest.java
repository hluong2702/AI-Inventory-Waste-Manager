package vn.inventoryai.subscription.payment;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.store.Store;
import vn.inventoryai.subscription.*;
import vn.inventoryai.subscription.dto.PaymentWebhookRequest;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayOsGatewayServiceTest {
    private static final String CHECKSUM_KEY = "test-checksum-key-never-used-in-production";

    @Test
    void createsRealPayOsPaymentLinkWithExactVndAmount() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService paymentRequests = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(paymentRequests);
        when(paymentRequests.create(org.mockito.ArgumentMatchers.any(CreatePaymentLinkRequest.class)))
                .thenAnswer(invocation -> {
                    CreatePaymentLinkRequest request = invocation.getArgument(0);
                    CreatePaymentLinkResponse response = new CreatePaymentLinkResponse();
                    response.setOrderCode(request.getOrderCode());
                    response.setCheckoutUrl("https://pay.payos.vn/web/real-link-id");
                    return response;
                });
        PayOsGatewayService gateway = new PayOsGatewayService(properties(), client);

        PaymentTransaction payment = payment();
        payment.setProviderTransactionId(gateway.reserveProviderTransactionId(payment));

        PaymentIntent intent = gateway.createPayment(payment, "127.0.0.1");

        ArgumentCaptor<CreatePaymentLinkRequest> captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequests).create(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(699_000L);
        assertThat(captor.getValue().getReturnUrl()).contains("payment=success");
        assertThat(captor.getValue().getCancelUrl()).contains("payment=failed");
        assertThat(intent.paymentUrl()).isEqualTo("https://pay.payos.vn/web/real-link-id");
        assertThat(intent.providerTransactionId()).isEqualTo(String.valueOf(captor.getValue().getOrderCode()));
        assertThat(intent.providerTransactionId()).isEqualTo("2000000000000042");
    }

    @Test
    void acceptsOnlyWebhookSignedByPayOsChecksumKey() {
        PayOS client = new PayOS("client-id", "api-key", CHECKSUM_KEY);
        PayOsGatewayService gateway = new PayOsGatewayService(properties(), client);
        Map<String, Object> data = webhookData();
        String signature = client.getCrypto().createSignatureFromObj(data, CHECKSUM_KEY);
        Map<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("code", "00");
        webhook.put("desc", "success");
        webhook.put("success", true);
        webhook.put("data", data);
        webhook.put("signature", signature);
        PaymentWebhookRequest request = new PaymentWebhookRequest("123456", PaymentStatus.SUCCESS, signature, null, webhook);

        assertThat(gateway.verifyWebhook(request)).isTrue();
        assertThat(gateway.validateWebhookPayment(payment(), request)).isTrue();

        data.put("amount", 1L);
        assertThat(gateway.verifyWebhook(request)).isFalse();
        assertThat(gateway.validateWebhookPayment(payment(), request)).isFalse();
    }

    @Test
    void recoversExistingProviderOrderWithoutCreatingADuplicate() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService paymentRequests = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(paymentRequests);
        PaymentLink existing = new PaymentLink();
        existing.setId("existing-link-id");
        existing.setOrderCode(2_000_000_000_000_042L);
        existing.setStatus(PaymentLinkStatus.PENDING);
        when(paymentRequests.get(2_000_000_000_000_042L)).thenReturn(existing);
        PayOsGatewayService gateway = new PayOsGatewayService(properties(), client);
        PaymentTransaction payment = payment();
        payment.setProviderTransactionId(gateway.reserveProviderTransactionId(payment));

        PaymentIntent recovered = gateway.recoverOrCreatePayment(payment, null);

        assertThat(recovered.providerTransactionId()).isEqualTo("2000000000000042");
        assertThat(recovered.paymentUrl()).isEqualTo("https://pay.payos.vn/web/existing-link-id");
        assertThat(recovered.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRequests, never()).create(org.mockito.ArgumentMatchers.any(CreatePaymentLinkRequest.class));
    }

    private PayOsProperties properties() {
        return new PayOsProperties(
                true,
                "client-id",
                "api-key",
                CHECKSUM_KEY,
                "https://app.example.com/billing?payment=success",
                "https://app.example.com/billing?payment=failed",
                15
        );
    }

    private Map<String, Object> webhookData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123456L);
        data.put("amount", 699000L);
        data.put("description", "GOI PRO 123456");
        data.put("accountNumber", "12345678");
        data.put("reference", "FT123");
        data.put("transactionDateTime", "2026-07-13 12:00:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "payment-link-id");
        data.put("code", "00");
        data.put("desc", "Thanh cong");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    private PaymentTransaction payment() {
        Store store = new Store();
        store.setId(42L);
        store.setName("Test Store");
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);

        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        plan.setId(3L);
        plan.setCode(SubscriptionPlan.PRO);
        plan.setName("Pro");
        plan.setPrice(new BigDecimal("699000.00"));
        plan.setCurrency("VND");
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setFeatureLimits("{}");

        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(10L);
        subscription.setTenant(store);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT);

        PaymentTransaction payment = new PaymentTransaction();
        payment.setId(42L);
        payment.setTenant(store);
        payment.setSubscription(subscription);
        payment.setAmount(plan.getPrice());
        payment.setCurrency("VND");
        payment.setProvider("PAYOS");
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }
}
