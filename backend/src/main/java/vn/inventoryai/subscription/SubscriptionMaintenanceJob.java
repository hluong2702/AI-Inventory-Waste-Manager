package vn.inventoryai.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class SubscriptionMaintenanceJob {
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final vn.inventoryai.subscription.payment.PaymentGatewayRegistry paymentGatewayRegistry;

    @Scheduled(cron = "0 5 2 * * *")
    @Transactional
    public void expireAndDowngradeSubscriptions() {
        var freePlan = planRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)
                .orElseThrow();
        tenantSubscriptionRepository.findByStatusAndEndDateLessThanEqual(SubscriptionStatus.ACTIVE, LocalDate.now())
                .forEach(active -> {
                    var nextPlan = active.getPendingDowngradePlan() == null ? freePlan : active.getPendingDowngradePlan();
                    subscriptionService.activateFreePlan(active.getTenant(), active, nextPlan);
                });
    }

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void reconcilePendingPayments() {
        paymentTransactionRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, java.time.Instant.now().minusSeconds(15 * 60))
                .forEach(payment -> {
                    var gateway = paymentGatewayRegistry.get(payment.getProvider());
                    var status = gateway.queryStatus(payment);
                    if (status == PaymentStatus.SUCCESS) {
                        payment.setStatus(PaymentStatus.SUCCESS);
                        payment.setUpdatedAt(java.time.Instant.now());
                        subscriptionService.activatePaidSubscription(payment.getSubscription());
                        paymentTransactionRepository.save(payment);
                    } else if (status == PaymentStatus.FAILED) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setUpdatedAt(java.time.Instant.now());
                        payment.getSubscription().setStatus(SubscriptionStatus.CANCELLED);
                        tenantSubscriptionRepository.save(payment.getSubscription());
                        paymentTransactionRepository.save(payment);
                    }
                });
    }
}
