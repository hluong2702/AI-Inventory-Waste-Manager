package vn.inventoryai.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.subscription.dto.PaymentWebhookRequest;
import vn.inventoryai.subscription.payment.PaymentGatewayRegistry;
import vn.inventoryai.subscription.payment.PaymentGatewayService;

import java.time.LocalDate;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceWebhookTest {
    @Mock SubscriptionPlanRepository planRepository;
    @Mock TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock vn.inventoryai.store.StoreRepository storeRepository;
    @Mock PaymentGatewayRegistry paymentGatewayRegistry;
    @Mock SubscriptionCacheService cacheService;
    @Mock PaymentGatewayService gateway;
    @Mock TransactionTemplate transactionTemplate;

    @Test
    void invalidWebhookSignatureCannotReachPaymentOrPlanMutation() {
        SubscriptionService service = service();
        PaymentWebhookRequest request = new PaymentWebhookRequest("provider-tx-1", PaymentStatus.SUCCESS, "invalid", null, java.util.Map.of());
        when(paymentGatewayRegistry.get("payos")).thenReturn(gateway);
        when(gateway.verifyWebhook(request)).thenReturn(false);

        assertThatThrownBy(() -> service.handleWebhook("payos", request))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(paymentTransactionRepository, tenantSubscriptionRepository, storeRepository, cacheService);
    }

    @Test
    void lateSuccessfulWebhookIsQuarantinedForManualReview() {
        SubscriptionService service = service();
        PaymentWebhookRequest request = new PaymentWebhookRequest("provider-tx-2", PaymentStatus.SUCCESS, "valid", null, java.util.Map.of());
        PaymentTransaction payment = payment(SubscriptionStatus.CANCELLED);
        payment.setStatus(PaymentStatus.CANCELLED);

        when(paymentGatewayRegistry.get("payos")).thenReturn(gateway);
        when(gateway.verifyWebhook(request)).thenReturn(true);
        when(gateway.validateWebhookPayment(payment, request)).thenReturn(true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId("PAYOS", "provider-tx-2"))
                .thenReturn(java.util.Optional.of(payment));
        when(gateway.provider()).thenReturn("PAYOS");

        var response = service.handleWebhook("payos", request);

        assertThat(response.status()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REVIEW_REQUIRED);
        verify(paymentTransactionRepository).save(payment);
        verifyNoInteractions(cacheService);
    }

    @Test
    void successfulWebhookFlushesPreviousActiveSubscriptionBeforePromotion() {
        SubscriptionService service = service();
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "provider-tx-success",
                PaymentStatus.SUCCESS,
                "valid",
                null,
                java.util.Map.of()
        );
        PaymentTransaction payment = payment(SubscriptionStatus.PENDING_PAYMENT);
        TenantSubscription pending = payment.getSubscription();
        TenantSubscription previousActive = subscription(
                21L,
                payment.getTenant(),
                vn.inventoryai.common.enums.SubscriptionPlan.FREE,
                SubscriptionStatus.ACTIVE
        );

        when(paymentGatewayRegistry.get("payos")).thenReturn(gateway);
        when(gateway.provider()).thenReturn("PAYOS");
        when(gateway.verifyWebhook(request)).thenReturn(true);
        when(gateway.validateWebhookPayment(payment, request)).thenReturn(true);
        when(paymentTransactionRepository.findByProviderAndProviderTransactionId("PAYOS", "provider-tx-success"))
                .thenReturn(Optional.of(payment));
        when(tenantSubscriptionRepository.isCurrentPending(10L, 20L)).thenReturn(true);
        when(tenantSubscriptionRepository.findActiveForUpdate(10L)).thenReturn(Optional.of(previousActive));

        var response = service.handleWebhook("payos", request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(previousActive.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        InOrder writes = inOrder(tenantSubscriptionRepository);
        writes.verify(tenantSubscriptionRepository).save(previousActive);
        writes.verify(tenantSubscriptionRepository).flush();
        writes.verify(tenantSubscriptionRepository).save(pending);
    }

    @Test
    void freeFallbackFlushesCancellationBeforeInsertingReplacement() {
        SubscriptionService service = service();
        vn.inventoryai.store.Store tenant = new vn.inventoryai.store.Store();
        tenant.setId(10L);
        TenantSubscription active = subscription(
                20L,
                tenant,
                vn.inventoryai.common.enums.SubscriptionPlan.PRO,
                SubscriptionStatus.ACTIVE
        );
        SubscriptionPlanEntity freePlan = plan(vn.inventoryai.common.enums.SubscriptionPlan.FREE);
        when(tenantSubscriptionRepository.save(any(TenantSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantSubscription replacement = service.activateFreeFallback(tenant, active, freePlan);

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(replacement.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        InOrder writes = inOrder(tenantSubscriptionRepository);
        writes.verify(tenantSubscriptionRepository).save(active);
        writes.verify(tenantSubscriptionRepository).flush();
        writes.verify(tenantSubscriptionRepository).save(replacement);
    }

    private SubscriptionService service() {
        return new SubscriptionService(
                planRepository,
                tenantSubscriptionRepository,
                paymentTransactionRepository,
                storeRepository,
                paymentGatewayRegistry,
                cacheService,
                new ObjectMapper(),
                transactionTemplate,
                Clock.systemUTC()
        );
    }

    private PaymentTransaction payment(SubscriptionStatus subscriptionStatus) {
        vn.inventoryai.store.Store tenant = new vn.inventoryai.store.Store();
        tenant.setId(10L);
        TenantSubscription subscription = subscription(
                20L,
                tenant,
                vn.inventoryai.common.enums.SubscriptionPlan.PRO,
                subscriptionStatus
        );
        PaymentTransaction payment = new PaymentTransaction();
        payment.setId(30L);
        payment.setTenant(tenant);
        payment.setSubscription(subscription);
        payment.setProvider("PAYOS");
        payment.setProviderTransactionId("provider-tx-2");
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private TenantSubscription subscription(
            Long id,
            vn.inventoryai.store.Store tenant,
            vn.inventoryai.common.enums.SubscriptionPlan code,
            SubscriptionStatus status
    ) {
        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(id);
        subscription.setTenant(tenant);
        subscription.setPlan(plan(code));
        subscription.setStatus(status);
        subscription.setStartDate(LocalDate.now());
        return subscription;
    }

    private SubscriptionPlanEntity plan(vn.inventoryai.common.enums.SubscriptionPlan code) {
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        plan.setCode(code);
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setFeatureLimits("{\"features\":[]}");
        return plan;
    }
}
