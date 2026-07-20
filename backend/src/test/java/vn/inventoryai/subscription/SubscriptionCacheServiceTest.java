package vn.inventoryai.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.inventoryai.common.cache.CacheProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SubscriptionCacheServiceTest {
    @Test
    void malformedCacheValueIsEvictedAndFallsBackToDatabase() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("inventoryai:test:v3:subscription:current:42"))
                .thenReturn("NOT_A_STATUS|PRO|EXPORT_REPORTS");
        SubscriptionCacheService service = new SubscriptionCacheService(
                redis,
                new ObjectMapper(),
                new CacheProperties("inventoryai:test:v3", Duration.ofMinutes(5))
        );

        var cached = service.getCurrent(42L);

        assertThat(cached).isEmpty();
        verify(redis).delete("inventoryai:test:v3:subscription:current:42");
    }
}
