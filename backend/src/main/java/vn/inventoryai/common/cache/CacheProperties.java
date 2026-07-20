package vn.inventoryai.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(String namespace, Duration subscriptionTtl) {
    public CacheProperties {
        namespace = namespace == null ? "" : namespace.trim();
        if (!namespace.matches("[A-Za-z0-9][A-Za-z0-9:_-]{2,79}")) {
            throw new IllegalArgumentException(
                    "app.cache.namespace must contain 3-80 safe characters and be unique per environment"
            );
        }
        if (subscriptionTtl == null
                || subscriptionTtl.compareTo(Duration.ofMinutes(1)) < 0
                || subscriptionTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("app.cache.subscription-ttl must be between 1 minute and 1 hour");
        }
    }
}
