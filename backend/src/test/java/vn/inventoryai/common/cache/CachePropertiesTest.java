package vn.inventoryai.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachePropertiesTest {
    @Test
    void acceptsEnvironmentScopedNamespaceAndBoundedTtl() {
        CacheProperties properties = new CacheProperties(
                "inventoryai:production:v3",
                Duration.ofMinutes(5)
        );

        assertThat(properties.namespace()).isEqualTo("inventoryai:production:v3");
        assertThat(properties.subscriptionTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsMissingNamespaceAndUnsafeTtl() {
        assertThatThrownBy(() -> new CacheProperties("", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("namespace");
        assertThatThrownBy(() -> new CacheProperties("inventoryai:prod", Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscription-ttl");
    }
}
