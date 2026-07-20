package vn.inventoryai.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.dto.*;
import vn.inventoryai.subscription.payment.PaymentGatewayRegistry;
import vn.inventoryai.subscription.payment.PaymentGatewayService;
import vn.inventoryai.subscription.payment.PaymentIntent;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionPlanRepository planRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StoreRepository storeRepository;
    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final SubscriptionCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> plans() {
        return planRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse current() {
        Long tenantId = currentTenantId();
        TenantSubscription subscription = tenantSubscriptionRepository.findActive(tenantId)
                .or(() -> tenantSubscriptionRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        return toCurrentResponse(subscription);
    }

    public UpgradeSubscriptionResponse changePlan(
            UpgradeSubscriptionRequest request,
            String clientIp,
            String requestedIdempotencyKey
    ) {
        Long tenantId = currentTenantId();
        String idempotencyKey = normalizeIdempotencyKey(requestedIdempotencyKey);
        CheckoutPreparation preparation = transactionTemplate.execute(status ->
                preparePlanChange(tenantId, request, idempotencyKey)
        );
        if (preparation == null) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to prepare payment");
        }
        if (preparation.immediateResponse() != null) {
            return preparation.immediateResponse();
        }

        PaymentGatewayService gateway = paymentGatewayRegistry.get(preparation.payment().getProvider());
        final PaymentIntent intent;
        try {
            intent = gateway.recoverOrCreatePayment(preparation.payment(), clientIp);
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status ->
                    recordPaymentCreationFailure(
                            preparation.payment().getId(),
                            ex,
                            gateway.isDefinitiveCreationFailure(ex)
                    )
            );
            if (ex instanceof AppException appException) throw appException;
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, HttpStatus.BAD_GATEWAY, "Payment provider is unavailable");
        }

        UpgradeSubscriptionResponse response = transactionTemplate.execute(status ->
                completePaymentCreation(preparation.payment().getId(), intent)
        );
        if (response == null) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to persist payment link");
        }
        return response;
    }

    private CheckoutPreparation preparePlanChange(
            Long tenantId,
            UpgradeSubscriptionRequest request,
            String idempotencyKey
    ) {
        Store tenant = storeRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantSubscription active = tenantSubscriptionRepository.findActiveForUpdate(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Active subscription not found"));

        // The active-subscription row serializes checkout preparation for a tenant.
        // Looking up idempotency only after this lock avoids the read-then-insert race.
        Optional<PaymentTransaction> existingPayment = paymentTransactionRepository
                .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existingPayment.isPresent()) {
            PaymentTransaction existing = existingPayment.get();
            if (existing.getSubscription().getPlan().getCode() != request.targetPlan()
                    || !existing.getProvider().equalsIgnoreCase(request.paymentProvider())
                    || !existing.getPaymentMethod().equalsIgnoreCase(request.paymentMethod())) {
                throw new AppException(
                        ErrorCode.IDEMPOTENCY_CONFLICT,
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used for a different plan change"
                );
            }
            if (existing.getStatus() == PaymentStatus.CREATING) {
                return new CheckoutPreparation(null, existing);
            }
            return new CheckoutPreparation(toUpgradeResponse(existing), null);
        }

        SubscriptionPlanEntity targetPlan = planRepository.findByCodeAndActiveTrue(request.targetPlan())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Target plan not found"));

        if (active.getPlan().getCode() == targetPlan.getCode()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Tenant is already on this plan");
        }

        if (targetPlan.getPrice().signum() == 0 && targetPlan.getCode() != SubscriptionPlan.FREE) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Zero-price commercial plans are not available through self-service upgrade"
            );
        }

        if (targetPlan.getPrice().compareTo(active.getPlan().getPrice()) <= 0) {
            if (targetPlan.getCode() != SubscriptionPlan.FREE) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        HttpStatus.CONFLICT,
                        "Paid downgrades require a new checkout when the current billing period ends"
                );
            }
            active.setPendingDowngradePlan(targetPlan);
            active.setAutoRenew(false);
            tenantSubscriptionRepository.save(active);
            cacheService.invalidateAfterCommit(tenantId);
            return new CheckoutPreparation(
                    new UpgradeSubscriptionResponse(
                            active.getId(), null, active.getStatus(), null, targetPlan.getPrice(),
                            targetPlan.getCurrency(), request.paymentProvider(), null, null
                    ),
                    null
            );
        }

        tenantSubscriptionRepository.findPendingForUpdate(tenantId).forEach(pending -> {
            pending.setStatus(SubscriptionStatus.CANCELLED);
            pending.setCancelledAt(Instant.now());
            tenantSubscriptionRepository.save(pending);
            paymentTransactionRepository.findBySubscriptionIdAndStatusIn(
                    pending.getId(),
                    List.of(
                            PaymentStatus.CREATING,
                            PaymentStatus.CREATION_RECONCILING,
                            PaymentStatus.PENDING,
                            PaymentStatus.RECONCILING
                    )
            ).forEach(payment -> {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setFailureReason("Superseded by a newer checkout");
                payment.setUpdatedAt(Instant.now());
                paymentTransactionRepository.save(payment);
            });
        });

        TenantSubscription pending = new TenantSubscription();
        pending.setTenant(tenant);
        pending.setPlan(targetPlan);
        pending.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        pending.setAutoRenew(true);
        pending.setStartDate(null);
        pending.setEndDate(null);
        pending = tenantSubscriptionRepository.save(pending);

        PaymentGatewayService gateway = paymentGatewayRegistry.get(request.paymentProvider());
        PaymentTransaction payment = new PaymentTransaction();
        payment.setTenant(tenant);
        payment.setSubscription(pending);
        payment.setAmount(targetPlan.getPrice());
        payment.setCurrency(targetPlan.getCurrency());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setProvider(gateway.provider());
        // A temporary reference exists only inside this local transaction. The
        // provider-specific reference is derived after the database id is assigned
        // and committed before any network request.
        payment.setProviderTransactionId("LOCAL-" + UUID.randomUUID());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PaymentStatus.CREATING);
        payment.setUpdatedAt(Instant.now());
        payment = paymentTransactionRepository.saveAndFlush(payment);
        payment.setProviderTransactionId(gateway.reserveProviderTransactionId(payment));
        payment = paymentTransactionRepository.save(payment);

        pending.getPlan().getCode();
        return new CheckoutPreparation(null, payment);
    }

    private UpgradeSubscriptionResponse completePaymentCreation(Long paymentId, PaymentIntent intent) {
        PaymentTransaction payment = paymentTransactionRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment transaction not found"));
        if (payment.getStatus() != PaymentStatus.CREATING) {
            return toUpgradeResponse(payment);
        }
        finalizePaymentCreation(payment, intent);
        return toUpgradeResponse(payment);
    }

    private void recordPaymentCreationFailure(Long paymentId, RuntimeException failure, boolean definitive) {
        paymentTransactionRepository.findByIdForUpdate(paymentId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.CREATING) return;
            payment.setFailureReason(safeFailureReason(failure));
            payment.setUpdatedAt(Instant.now());
            if (definitive) {
                payment.setStatus(PaymentStatus.FAILED);
                cancelPendingSubscription(payment.getSubscription());
            }
            paymentTransactionRepository.save(payment);
        });
    }

    private String safeFailureReason(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        return type.length() <= 255 ? type : type.substring(0, 255);
    }

    private String normalizeIdempotencyKey(String requestedKey) {
        if (requestedKey == null || requestedKey.isBlank()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required"
            );
        }
        String normalized = requestedKey.trim();
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1-128 safe ASCII characters"
            );
        }
        return normalized;
    }

    private UpgradeSubscriptionResponse toUpgradeResponse(PaymentTransaction payment) {
        return new UpgradeSubscriptionResponse(
                payment.getSubscription().getId(),
                payment.getId(),
                payment.getSubscription().getStatus(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getProviderTransactionId().startsWith("LOCAL-") ? null : payment.getProviderTransactionId(),
                payment.getPaymentUrl()
        );
    }

    @Transactional
    public PaymentWebhookResponse handleWebhook(String provider, PaymentWebhookRequest request) {
        PaymentGatewayService gateway = paymentGatewayRegistry.get(provider);
        if (!gateway.verifyWebhook(request)) {
            throw new AppException(ErrorCode.PAYMENT_SIGNATURE_INVALID, HttpStatus.FORBIDDEN, "Invalid payment webhook signature");
        }

        PaymentTransaction payment = paymentTransactionRepository
                .findByProviderAndProviderTransactionId(gateway.provider(), request.providerTransactionId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (!gateway.validateWebhookPayment(payment, request)) {
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, HttpStatus.CONFLICT, "Payment webhook amount or merchant does not match transaction");
        }

        if (request.status() == PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.SUCCESS) {
            if (payment.getStatus() == PaymentStatus.FAILED
                    || payment.getStatus() == PaymentStatus.CANCELLED
                    || payment.getStatus() == PaymentStatus.EXPIRED
                    || payment.getStatus() == PaymentStatus.REFUNDED) {
                payment.setStatus(PaymentStatus.REVIEW_REQUIRED);
                payment.setFailureReason("Provider reported payment after the local transaction was closed");
                payment.setUpdatedAt(Instant.now());
                paymentTransactionRepository.save(payment);
                return new PaymentWebhookResponse(payment.getId(), payment.getStatus(), true);
            }
            if (payment.getStatus() == PaymentStatus.REVIEW_REQUIRED) {
                return new PaymentWebhookResponse(payment.getId(), payment.getStatus(), false);
            }
        } else if (isTerminal(payment.getStatus())) {
            return new PaymentWebhookResponse(payment.getId(), payment.getStatus(), false);
        }

        payment.setStatus(request.status());
        payment.setFailureReason(request.failureReason());
        payment.setUpdatedAt(Instant.now());

        if (request.status() == PaymentStatus.SUCCESS) {
            applySuccessfulPayment(payment, "Late payment for a superseded or cancelled subscription");
        } else if (request.status() == PaymentStatus.FAILED
                || request.status() == PaymentStatus.CANCELLED
                || request.status() == PaymentStatus.EXPIRED) {
            cancelPendingSubscription(payment.getSubscription());
        }

        paymentTransactionRepository.save(payment);
        return new PaymentWebhookResponse(payment.getId(), payment.getStatus(), true);
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED
                || status == PaymentStatus.EXPIRED
                || status == PaymentStatus.REVIEW_REQUIRED
                || status == PaymentStatus.REFUNDED;
    }

    private void finalizePaymentCreation(PaymentTransaction payment, PaymentIntent intent) {
        if (!payment.getProviderTransactionId().equals(intent.providerTransactionId())) {
            payment.setStatus(PaymentStatus.REVIEW_REQUIRED);
            payment.setFailureReason("Payment provider returned a mismatched transaction reference");
            payment.setUpdatedAt(Instant.now());
            paymentTransactionRepository.save(payment);
            return;
        }

        payment.setPaymentUrl(intent.paymentUrl());
        payment.setFailureReason(null);
        payment.setUpdatedAt(Instant.now());
        PaymentStatus providerStatus = intent.status() == null ? PaymentStatus.PENDING : intent.status();
        if (providerStatus == PaymentStatus.SUCCESS) {
            payment.setStatus(PaymentStatus.SUCCESS);
            applySuccessfulPayment(payment, "Provider reports paid for a superseded or cancelled subscription");
        } else if (providerStatus == PaymentStatus.FAILED
                || providerStatus == PaymentStatus.CANCELLED
                || providerStatus == PaymentStatus.EXPIRED) {
            payment.setStatus(providerStatus);
            cancelPendingSubscription(payment.getSubscription());
        } else {
            payment.setStatus(PaymentStatus.PENDING);
        }
        paymentTransactionRepository.save(payment);
    }

    private void applySuccessfulPayment(PaymentTransaction payment, String latePaymentReason) {
        TenantSubscription pending = payment.getSubscription();
        Long tenantId = payment.getTenant().getId();
        if (pending.getStatus() != SubscriptionStatus.PENDING_PAYMENT
                || !tenantSubscriptionRepository.isCurrentPending(tenantId, pending.getId())) {
            payment.setStatus(PaymentStatus.REVIEW_REQUIRED);
            payment.setFailureReason(latePaymentReason);
            return;
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setFailureReason(null);
        activatePaidSubscription(pending);
    }

    private void cancelPendingSubscription(TenantSubscription pending) {
        if (pending.getStatus() == SubscriptionStatus.PENDING_PAYMENT) {
            pending.setStatus(SubscriptionStatus.CANCELLED);
            pending.setCancelledAt(Instant.now());
            tenantSubscriptionRepository.save(pending);
        }
    }

    @Transactional
    public CurrentSubscriptionResponse cancel(CancelSubscriptionRequest request) {
        if (!request.cancelAutoRenew()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "cancelAutoRenew must be true for a period-end cancellation"
            );
        }
        Long tenantId = currentTenantId();
        TenantSubscription active = tenantSubscriptionRepository.findActiveForUpdate(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Active subscription not found"));
        SubscriptionPlanEntity free = planRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Free plan not found"));
        active.setAutoRenew(false);
        active.setPendingDowngradePlan(free);
        tenantSubscriptionRepository.save(active);
        cacheService.invalidateAfterCommit(tenantId);
        return toCurrentResponse(active);
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(Long tenantId, String feature) {
        return cacheService.getCurrent(tenantId)
                .map(cached -> cached.status() == SubscriptionStatus.ACTIVE && cached.features().contains(feature))
                .orElseGet(() -> {
                    TenantSubscription active = tenantSubscriptionRepository.findActive(tenantId)
                            .orElse(null);
                    if (active == null) return false;
                    cacheService.cacheCurrent(tenantId, active);
                    return hasFeatureInLimits(active.getPlan().getFeatureLimits(), feature);
                });
    }

    private void activatePaidSubscription(TenantSubscription pending) {
        if (pending.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
            throw new AppException(
                    ErrorCode.PAYMENT_REVIEW_REQUIRED,
                    HttpStatus.CONFLICT,
                    "Only a current pending subscription can be activated"
            );
        }
        Long tenantId = pending.getTenant().getId();
        tenantSubscriptionRepository.findActiveForUpdate(tenantId).ifPresent(active -> {
            if (!active.getId().equals(pending.getId())) {
                active.setStatus(SubscriptionStatus.CANCELLED);
                active.setCancelledAt(Instant.now());
                tenantSubscriptionRepository.save(active);
                // Release the generated unique active_tenant_id before the pending
                // row is promoted. Hibernate does not guarantee a safe update order.
                tenantSubscriptionRepository.flush();
            }
        });

        pending.setStatus(SubscriptionStatus.ACTIVE);
        pending.setStartDate(LocalDate.now(clock));
        pending.setEndDate(endDateFor(pending.getPlan()));
        pending.setActivatedAt(Instant.now());
        pending.setAutoRenew(false);
        pending.setPendingDowngradePlan(null);
        tenantSubscriptionRepository.save(pending);
        syncStorePlan(tenantId, pending.getPlan());
        cacheService.cacheCurrentAfterCommit(tenantId, pending);
    }

    TenantSubscription activateFreeFallback(Store tenant, TenantSubscription active, SubscriptionPlanEntity freePlan) {
        if (freePlan.getCode() != SubscriptionPlan.FREE) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.CONFLICT,
                    "Only the Free plan may be activated without a successful payment"
            );
        }
        active.setStatus(SubscriptionStatus.CANCELLED);
        active.setCancelledAt(Instant.now());
        tenantSubscriptionRepository.save(active);
        // Entity inserts are normally flushed before updates. Flush the cancellation
        // first so the one-active-subscription database invariant remains satisfiable.
        tenantSubscriptionRepository.flush();

        TenantSubscription free = new TenantSubscription();
        free.setTenant(tenant);
        free.setPlan(freePlan);
        free.setStatus(SubscriptionStatus.ACTIVE);
        free.setStartDate(LocalDate.now(clock));
        free.setEndDate(endDateFor(freePlan));
        free.setAutoRenew(false);
        free.setActivatedAt(Instant.now());
        free = tenantSubscriptionRepository.save(free);
        syncStorePlan(tenant.getId(), freePlan);
        cacheService.cacheCurrentAfterCommit(tenant.getId(), free);
        return free;
    }

    @Transactional
    public Optional<PaymentTransaction> claimPaymentForReconciliation(Long paymentId) {
        int claimed = paymentTransactionRepository.claimForReconciliation(paymentId, Instant.now());
        if (claimed != 1) return Optional.empty();
        return paymentTransactionRepository.findByIdForUpdate(paymentId);
    }

    @Transactional
    public Optional<PaymentTransaction> claimPaymentCreationForReconciliation(Long paymentId) {
        int claimed = paymentTransactionRepository.claimCreationForReconciliation(paymentId, Instant.now());
        if (claimed != 1) return Optional.empty();
        return paymentTransactionRepository.findByIdForUpdate(paymentId);
    }

    @Transactional
    public void completePaymentCreationReconciliation(Long paymentId, PaymentIntent intent) {
        PaymentTransaction payment = paymentTransactionRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment transaction not found"));
        if (payment.getStatus() != PaymentStatus.CREATION_RECONCILING) return;
        finalizePaymentCreation(payment, intent);
    }

    @Transactional
    public void releasePaymentCreationReconciliation(Long paymentId) {
        paymentTransactionRepository.findByIdForUpdate(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CREATION_RECONCILING) {
                payment.setStatus(PaymentStatus.CREATING);
                payment.setUpdatedAt(Instant.now());
                paymentTransactionRepository.save(payment);
            }
        });
    }

    @Transactional
    public void completePaymentReconciliation(Long paymentId, PaymentStatus providerStatus) {
        PaymentTransaction payment = paymentTransactionRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment transaction not found"));
        if (payment.getStatus() != PaymentStatus.RECONCILING) return;

        if (providerStatus == PaymentStatus.SUCCESS) {
            applySuccessfulPayment(payment, "Provider reports paid for a superseded or cancelled subscription");
        } else if (providerStatus == PaymentStatus.FAILED
                || providerStatus == PaymentStatus.CANCELLED
                || providerStatus == PaymentStatus.EXPIRED) {
            payment.setStatus(providerStatus);
            cancelPendingSubscription(payment.getSubscription());
        } else {
            payment.setStatus(PaymentStatus.PENDING);
        }
        payment.setUpdatedAt(Instant.now());
        paymentTransactionRepository.save(payment);
    }

    @Transactional
    public void releasePaymentReconciliation(Long paymentId) {
        paymentTransactionRepository.findByIdForUpdate(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.RECONCILING) {
                payment.setStatus(PaymentStatus.PENDING);
                payment.setUpdatedAt(Instant.now());
                paymentTransactionRepository.save(payment);
            }
        });
    }

    @Transactional
    public void expireSubscription(Long subscriptionId, LocalDate businessDate) {
        TenantSubscription active = tenantSubscriptionRepository.findByIdForUpdate(subscriptionId).orElse(null);
        if (active == null
                || active.getStatus() != SubscriptionStatus.ACTIVE
                || active.getEndDate() == null
                || active.getEndDate().isAfter(businessDate)) {
            return;
        }

        if (active.getPlan().getCode() == SubscriptionPlan.FREE) {
            active.setStartDate(businessDate);
            active.setEndDate(businessDate.plusMonths(1));
            active.setPendingDowngradePlan(null);
            active.setAutoRenew(false);
            tenantSubscriptionRepository.save(active);
            cacheService.cacheCurrentAfterCommit(active.getTenant().getId(), active);
            return;
        }

        SubscriptionPlanEntity freePlan = planRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Free plan not found"));
        activateFreeFallback(active.getTenant(), active, freePlan);
    }

    private void syncStorePlan(Long tenantId, SubscriptionPlanEntity plan) {
        storeRepository.findById(tenantId).ifPresent(store -> {
            store.setSubscriptionPlan(plan.getCode());
            storeRepository.save(store);
        });
    }

    private boolean hasFeatureInLimits(String json, String feature) {
        try {
            JsonNode features = objectMapper.readTree(json).path("features");
            if (!features.isArray()) {
                return false;
            }
            String normalizedFeature = feature.toUpperCase(Locale.ROOT);
            for (JsonNode item : features) {
                if (normalizedFeature.equals(item.asText().toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private LocalDate endDateFor(SubscriptionPlanEntity plan) {
        LocalDate businessDate = LocalDate.now(clock);
        return plan.getBillingCycle() == BillingCycle.YEARLY
                ? businessDate.plusYears(1)
                : businessDate.plusMonths(1);
    }

    private Long currentTenantId() {
        Long tenantId = SecurityUtils.storeId();
        if (tenantId == null) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "System admin must select a tenant");
        }
        return tenantId;
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlanEntity plan) {
        return new SubscriptionPlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getPrice(), plan.getCurrency(), plan.getBillingCycle(), plan.getFeatureLimits());
    }

    private CurrentSubscriptionResponse toCurrentResponse(TenantSubscription subscription) {
        return new CurrentSubscriptionResponse(
                subscription.getId(),
                subscription.getPlan().getCode(),
                subscription.getStatus(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.isAutoRenew(),
                subscription.getPendingDowngradePlan() == null ? null : subscription.getPendingDowngradePlan().getCode(),
                subscription.getPlan().getFeatureLimits()
        );
    }

    private record CheckoutPreparation(
            UpgradeSubscriptionResponse immediateResponse,
            PaymentTransaction payment
    ) {
    }
}
