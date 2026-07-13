package vn.inventoryai.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionCacheService {
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void cacheCurrent(Long tenantId, TenantSubscription subscription) {
        String value = subscription.getStatus().name()
                + "|" + subscription.getPlan().getCode().name()
                + "|" + extractFeatures(subscription.getPlan().getFeatureLimits());
        redisTemplate.opsForValue().set(key(tenantId), value, TTL);
    }

    public Optional<CachedSubscription> getCurrent(Long tenantId) {
        String value = redisTemplate.opsForValue().get(key(tenantId));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length < 3) {
            return Optional.empty();
        }
        Set<String> features = parts[2].isBlank()
                ? Set.of()
                : Arrays.stream(parts[2].split(",")).collect(Collectors.toUnmodifiableSet());
        return Optional.of(new CachedSubscription(SubscriptionStatus.valueOf(parts[0]), parts[1], features));
    }

    public void invalidate(Long tenantId) {
        redisTemplate.delete(key(tenantId));
    }

    private String key(Long tenantId) {
        return "subscription:current:" + tenantId;
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

    public record CachedSubscription(
            SubscriptionStatus status,
            String plan,
            Set<String> features
    ) {
    }
}
