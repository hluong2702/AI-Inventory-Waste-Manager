package vn.inventoryai.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.inventoryai.common.cache.CacheProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionCacheService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public void cacheCurrent(Long tenantId, TenantSubscription subscription) {
        try {
            String value = subscription.getStatus().name()
                    + "|" + subscription.getPlan().getCode().name()
                    + "|" + extractFeatures(subscription.getPlan().getFeatureLimits());
            redisTemplate.opsForValue().set(key(tenantId), value, ttl(tenantId));
        } catch (RuntimeException ex) {
            log.warn("Subscription cache write failed for tenantId={}", tenantId, ex);
        }
    }

    public void cacheCurrentAfterCommit(Long tenantId, TenantSubscription subscription) {
        String status = subscription.getStatus().name();
        String plan = subscription.getPlan().getCode().name();
        String features = extractFeatures(subscription.getPlan().getFeatureLimits());
        afterCommit(() -> cacheValue(tenantId, status, plan, features));
    }

    public Optional<CachedSubscription> getCurrent(Long tenantId) {
        final String value;
        try {
            value = redisTemplate.opsForValue().get(key(tenantId));
        } catch (RuntimeException ex) {
            log.warn("Subscription cache read failed for tenantId={}; falling back to database", tenantId, ex);
            return Optional.empty();
        }
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            String[] parts = value.split("\\|", 3);
            if (parts.length < 3) {
                invalidate(tenantId);
                return Optional.empty();
            }
            Set<String> features = parts[2].isBlank()
                    ? Set.of()
                    : Arrays.stream(parts[2].split(",")).collect(Collectors.toUnmodifiableSet());
            return Optional.of(new CachedSubscription(SubscriptionStatus.valueOf(parts[0]), parts[1], features));
        } catch (RuntimeException ex) {
            log.warn("Invalid subscription cache value for tenantId={}; falling back to database", tenantId);
            invalidate(tenantId);
            return Optional.empty();
        }
    }

    public void invalidate(Long tenantId) {
        try {
            redisTemplate.delete(key(tenantId));
        } catch (RuntimeException ex) {
            log.warn("Subscription cache invalidation failed for tenantId={}", tenantId, ex);
        }
    }

    public void invalidateAfterCommit(Long tenantId) {
        afterCommit(() -> invalidate(tenantId));
    }

    private String key(Long tenantId) {
        return cacheProperties.namespace() + ":subscription:current:" + tenantId;
    }

    private void cacheValue(Long tenantId, String status, String plan, String features) {
        try {
            redisTemplate.opsForValue().set(
                    key(tenantId),
                    status + "|" + plan + "|" + features,
                    ttl(tenantId)
            );
        } catch (RuntimeException ex) {
            log.warn("Subscription cache write failed for tenantId={}", tenantId, ex);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private String extractFeatures(String featureLimits) {
        try {
            JsonNode features = objectMapper.readTree(featureLimits).path("features");
            Set<String> values = new HashSet<>();
            if (features.isArray()) {
                features.forEach(feature -> values.add(feature.asText()));
            }
            return String.join(",", values);
        } catch (Exception ex) {
            return "";
        }
    }

    private Duration ttl(Long tenantId) {
        return cacheProperties.subscriptionTtl().plusSeconds(Math.floorMod(tenantId, 60));
    }

    public record CachedSubscription(
            SubscriptionStatus status,
            String plan,
            Set<String> features
    ) {
    }
}
