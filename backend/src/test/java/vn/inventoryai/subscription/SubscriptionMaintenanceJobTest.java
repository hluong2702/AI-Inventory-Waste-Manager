package vn.inventoryai.subscription;

import org.junit.jupiter.api.Test;
import vn.inventoryai.subscription.payment.PaymentGatewayRegistry;
import vn.inventoryai.subscription.payment.PaymentGatewayService;
import vn.inventoryai.subscription.payment.PaymentIntent;
import vn.inventoryai.common.observability.OperationalMetrics;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionMaintenanceJobTest {

    @Test
    void successfulProviderQueryIsReconciledThroughSubscriptionStateMachine() {
        TenantSubscriptionRepository tenantSubscriptionRepository = mock(TenantSubscriptionRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        PaymentTransactionRepository paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayService gateway = mock(PaymentGatewayService.class);
        PaymentTransaction payment = new PaymentTransaction();
        payment.setProvider("PAYOS");
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentTransactionRepository.findReconciliationCandidates(any(Instant.class), any()))
                .thenReturn(List.of(42L));
        when(paymentTransactionRepository.findCreationReconciliationCandidates(any(Instant.class), any()))
                .thenReturn(List.of());
        when(subscriptionService.claimPaymentForReconciliation(42L)).thenReturn(java.util.Optional.of(payment));
        when(gatewayRegistry.get("PAYOS")).thenReturn(gateway);
        when(gateway.queryStatus(payment)).thenReturn(PaymentStatus.SUCCESS);

        SubscriptionMaintenanceJob job = new SubscriptionMaintenanceJob(
                tenantSubscriptionRepository,
                subscriptionService,
                paymentTransactionRepository,
                gatewayRegistry,
                Clock.systemUTC(),
                mock(OperationalMetrics.class)
        );
        job.reconcilePendingPayments();

        verify(subscriptionService).completePaymentReconciliation(42L, PaymentStatus.SUCCESS);
    }

    @Test
    void uncertainCreationIsRecoveredWithTheReservedProviderReference() {
        TenantSubscriptionRepository tenantSubscriptionRepository = mock(TenantSubscriptionRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        PaymentTransactionRepository paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        PaymentGatewayRegistry gatewayRegistry = mock(PaymentGatewayRegistry.class);
        PaymentGatewayService gateway = mock(PaymentGatewayService.class);
        PaymentTransaction payment = new PaymentTransaction();
        payment.setProvider("PAYOS");
        payment.setProviderTransactionId("2000000000000042");
        payment.setStatus(PaymentStatus.CREATION_RECONCILING);
        PaymentIntent recovered = new PaymentIntent(
                "2000000000000042",
                "https://pay.payos.vn/web/recovered",
                PaymentStatus.PENDING
        );

        when(paymentTransactionRepository.findCreationReconciliationCandidates(any(Instant.class), any()))
                .thenReturn(List.of(42L));
        when(paymentTransactionRepository.findReconciliationCandidates(any(Instant.class), any()))
                .thenReturn(List.of());
        when(subscriptionService.claimPaymentCreationForReconciliation(42L))
                .thenReturn(java.util.Optional.of(payment));
        when(gatewayRegistry.get("PAYOS")).thenReturn(gateway);
        when(gateway.recoverOrCreatePayment(payment, null)).thenReturn(recovered);

        SubscriptionMaintenanceJob job = new SubscriptionMaintenanceJob(
                tenantSubscriptionRepository,
                subscriptionService,
                paymentTransactionRepository,
                gatewayRegistry,
                Clock.systemUTC(),
                mock(OperationalMetrics.class)
        );
        job.reconcilePendingPayments();

        verify(subscriptionService).completePaymentCreationReconciliation(42L, recovered);
    }
}
