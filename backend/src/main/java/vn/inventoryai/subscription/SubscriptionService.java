package vn.inventoryai.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.store.SubscriptionRepository;
import vn.inventoryai.subscription.dto.*;
import vn.inventoryai.subscription.payment.PaymentGatewayRegistry;
import vn.inventoryai.subscription.payment.PaymentGatewayService;
import vn.inventoryai.subscription.payment.PaymentIntent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionPlanRepository planRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StoreRepository storeRepository;
    private final SubscriptionRepository legacySubscriptionRepository;
    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final SubscriptionCacheService cacheService;
    private final ObjectMapper objectMapper;

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

    @Transactional
    public UpgradeSubscriptionResponse changePlan(UpgradeSubscriptionRequest request) {
        Long tenantId = currentTenantId();
        Store tenant = storeRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantSubscription active = tenantSubscriptionRepository.findActiveForUpdate(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Active subscription not found"));
        SubscriptionPlanEntity targetPlan = planRepository.findByCodeAndActiveTrue(request.targetPlan())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Target plan not found"));

        if (active.getPlan().getCode() == targetPlan.getCode()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Tenant is already on this plan");
        }

        if (targetPlan.getPrice().compareTo(active.getPlan().getPrice()) < 0) {
            active.setPendingDowngradePlan(targetPlan);
            active.setAutoRenew(false);
            tenantSubscriptionRepository.save(active);
            cacheService.invalidate(tenantId);
            return new UpgradeSubscriptionResponse(active.getId(), null, active.getStatus(), null, targetPlan.getPrice(), targetPlan.getCurrency(), request.paymentProvider(), null, null);
        }

        if (targetPlan.getPrice().signum() == 0) {
            TenantSubscription activated = activateFreePlan(tenant, active, targetPlan);
            return new UpgradeSubscriptionResponse(activated.getId(), null, activated.getStatus(), null, targetPlan.getPrice(), targetPlan.getCurrency(), request.paymentProvider(), null, null);
        }

        tenantSubscriptionRepository.findPendingForUpdate(tenantId).forEach(pending -> {
            pending.setStatus(SubscriptionStatus.CANCELLED);
            pending.setCancelledAt(Instant.now());
            tenantSubscriptionRepository.save(pending);
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
        payment.setStatus(PaymentStatus.PENDING);
        PaymentIntent intent = gateway.createPayment(payment);
        payment.setProviderTransactionId(intent.providerTransactionId());
        payment.setPaymentUrl(intent.paymentUrl());
        payment = paymentTransactionRepository.save(payment);

        return new UpgradeSubscriptionResponse(
                pending.getId(),
                payment.getId(),
                pending.getStatus(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getProviderTransactionId(),
                payment.getPaymentUrl()
        );
    }

    @Transactional
    public PaymentWebhookResponse handleWebhook(String provider, PaymentWebhookRequest request) {
        PaymentGatewayService gateway = paymentGatewayRegistry.get(provider);
        if (!gateway.verifyWebhook(request)) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Invalid payment webhook signature");
        }

        PaymentTransaction payment = paymentTransactionRepository
                .findByProviderAndProviderTransactionId(gateway.provider(), request.providerTransactionId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return new PaymentWebhookResponse(payment.getId(), payment.getStatus(), false);
        }

        payment.setStatus(request.status());
        payment.setFailureReason(request.failureReason());
        payment.setUpdatedAt(Instant.now());

        if (request.status() == PaymentStatus.SUCCESS) {
            activatePaidSubscription(payment.getSubscription());
        } else if (request.status() == PaymentStatus.FAILED) {
            TenantSubscription pending = payment.getSubscription();
            pending.setStatus(SubscriptionStatus.CANCELLED);
            pending.setCancelledAt(Instant.now());
            tenantSubscriptionRepository.save(pending);
        }

        paymentTransactionRepository.save(payment);
        return new PaymentWebhookResponse(payment.getId(), payment.getStatus(), true);
    }

    @Transactional
    public CurrentSubscriptionResponse cancel(CancelSubscriptionRequest request) {
        Long tenantId = currentTenantId();
        TenantSubscription active = tenantSubscriptionRepository.findActiveForUpdate(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Active subscription not found"));
        SubscriptionPlanEntity free = planRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Free plan not found"));
        active.setAutoRenew(false);
        active.setPendingDowngradePlan(free);
        tenantSubscriptionRepository.save(active);
        cacheService.invalidate(tenantId);
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

    void activatePaidSubscription(TenantSubscription pending) {
        Long tenantId = pending.getTenant().getId();
        tenantSubscriptionRepository.findActiveForUpdate(tenantId).ifPresent(active -> {
            if (!active.getId().equals(pending.getId())) {
                active.setStatus(SubscriptionStatus.CANCELLED);
                active.setCancelledAt(Instant.now());
                tenantSubscriptionRepository.save(active);
            }
        });

        pending.setStatus(SubscriptionStatus.ACTIVE);
        pending.setStartDate(LocalDate.now());
        pending.setEndDate(endDateFor(pending.getPlan()));
        pending.setActivatedAt(Instant.now());
        pending.setAutoRenew(true);
        tenantSubscriptionRepository.save(pending);
        syncLegacySubscription(tenantId, pending.getPlan());
        cacheService.cacheCurrent(tenantId, pending);
    }

    TenantSubscription activateFreePlan(Store tenant, TenantSubscription active, SubscriptionPlanEntity freePlan) {
        active.setStatus(SubscriptionStatus.CANCELLED);
        active.setCancelledAt(Instant.now());
        tenantSubscriptionRepository.save(active);

        TenantSubscription free = new TenantSubscription();
        free.setTenant(tenant);
        free.setPlan(freePlan);
        free.setStatus(SubscriptionStatus.ACTIVE);
        free.setStartDate(LocalDate.now());
        free.setEndDate(endDateFor(freePlan));
        free.setAutoRenew(false);
        free.setActivatedAt(Instant.now());
        free = tenantSubscriptionRepository.save(free);
        syncLegacySubscription(tenant.getId(), freePlan);
        cacheService.cacheCurrent(tenant.getId(), free);
        return free;
    }

    private void syncLegacySubscription(Long tenantId, SubscriptionPlanEntity plan) {
        storeRepository.findById(tenantId).ifPresent(store -> {
            store.setSubscriptionPlan(plan.getCode());
            storeRepository.save(store);
        });
        legacySubscriptionRepository.findByStoreId(tenantId).ifPresent(subscription -> {
            subscription.setPlan(plan.getCode());
            subscription.setMaxStaff(extractLimit(plan.getFeatureLimits(), "staff"));
            subscription.setMaxIngredients(extractLimit(plan.getFeatureLimits(), "ingredients"));
            subscription.setExpiresAt(endDateFor(plan));
            subscription.setActive(true);
            legacySubscriptionRepository.save(subscription);
        });
    }

    private Integer extractLimit(String json, String key) {
        try {
            JsonNode value = objectMapper.readTree(json).path(key);
            return value.isMissingNode() || value.isNull() ? null : value.asInt();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Invalid subscription feature limits");
        }
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
        return plan.getBillingCycle() == BillingCycle.YEARLY ? LocalDate.now().plusYears(1) : LocalDate.now().plusMonths(1);
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
}
