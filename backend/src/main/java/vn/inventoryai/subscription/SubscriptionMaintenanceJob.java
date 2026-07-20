package vn.inventoryai.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.observability.OperationalMetrics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionMaintenanceJob {
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final vn.inventoryai.subscription.payment.PaymentGatewayRegistry paymentGatewayRegistry;
    private final Clock clock;
    private final OperationalMetrics operationalMetrics;

    @Scheduled(cron = "0 5 2 * * *", zone = "${app.business.time-zone:Asia/Ho_Chi_Minh}")
    public void expireAndDowngradeSubscriptions() {
        LocalDate today = LocalDate.now(clock);
        tenantSubscriptionRepository.findByStatusAndEndDateLessThanEqual(SubscriptionStatus.ACTIVE, today)
                .stream()
                .map(TenantSubscription::getId)
                .forEach(id -> subscriptionService.expireSubscription(id, today));
    }

    @Scheduled(cron = "${app.payment.reconciliation-cron:0 */2 * * * *}")
    public void reconcilePendingPayments() {
        paymentTransactionRepository
                .findCreationReconciliationCandidates(Instant.now().minusSeconds(2 * 60), PageRequest.of(0, 100))
                .forEach(this::reconcileCreation);
        paymentTransactionRepository
                .findReconciliationCandidates(Instant.now().minusSeconds(15 * 60), PageRequest.of(0, 100))
                .forEach(this::reconcileOne);
    }

    private void reconcileCreation(Long paymentId) {
        var claimed = subscriptionService.claimPaymentCreationForReconciliation(paymentId);
        if (claimed.isEmpty()) return;
        PaymentTransaction payment = claimed.get();
        try {
            var gateway = paymentGatewayRegistry.get(payment.getProvider());
            var intent = gateway.recoverOrCreatePayment(payment, null);
            subscriptionService.completePaymentCreationReconciliation(paymentId, intent);
            operationalMetrics.paymentReconciliation("creation", "success");
        } catch (RuntimeException ex) {
            operationalMetrics.paymentReconciliation("creation", "failure");
            log.warn("Payment creation reconciliation failed for paymentId={}", paymentId, ex);
            subscriptionService.releasePaymentCreationReconciliation(paymentId);
        }
    }

    private void reconcileOne(Long paymentId) {
        var claimed = subscriptionService.claimPaymentForReconciliation(paymentId);
        if (claimed.isEmpty()) return;
        PaymentTransaction payment = claimed.get();
        try {
            var gateway = paymentGatewayRegistry.get(payment.getProvider());
            PaymentStatus providerStatus = gateway.queryStatus(payment);
            subscriptionService.completePaymentReconciliation(paymentId, providerStatus);
            operationalMetrics.paymentReconciliation("status", "success");
        } catch (RuntimeException ex) {
            operationalMetrics.paymentReconciliation("status", "failure");
            log.warn("Payment reconciliation failed for paymentId={}", paymentId, ex);
            subscriptionService.releasePaymentReconciliation(paymentId);
        }
    }
}
