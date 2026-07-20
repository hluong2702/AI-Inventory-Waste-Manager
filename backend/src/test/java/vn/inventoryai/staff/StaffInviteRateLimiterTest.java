package vn.inventoryai.staff;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import vn.inventoryai.common.error.AppException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StaffInviteRateLimiterTest {
    @Test
    void executesAllRateLimitLayersInOneAtomicRedisScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        StaffInviteRateLimiter limiter = limiter(redisTemplate);

        limiter.check(7L, 10L, "Staff@Coffee.vn");

        @SuppressWarnings("unchecked")
        var keysCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any(Object[].class));
        assertThat(keysCaptor.getValue()).hasSize(3);
        assertThat(keysCaptor.getValue().toString())
                .contains("actor:7:store:10")
                .contains("store:10")
                .doesNotContain("staff@coffee.vn");
    }

    @Test
    void rejectsWhenAnyAtomicRedisLayerIsExceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn(3L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        StaffInviteRateLimiter limiter = limiter(redisTemplate);

        assertThatThrownBy(() -> limiter.check(7L, 10L, "staff@coffee.vn"))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void fallsBackToBoundedInMemoryCountersWhenRedisUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        StaffInviteRateLimiter limiter = limiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "maxInvitations", 2);
        ReflectionTestUtils.setField(limiter, "maxPerStore", 100);
        ReflectionTestUtils.setField(limiter, "maxPerRecipient", 100);

        limiter.check(7L, 10L, "staff@coffee.vn");
        limiter.check(7L, 10L, "staff@coffee.vn");

        assertThatThrownBy(() -> limiter.check(7L, 10L, "staff@coffee.vn"))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void localFallbackEvictsOldestKeysAtConfiguredCapacity() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
        StaffInviteRateLimiter limiter = limiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "fallbackMaxKeys", 3);
        ReflectionTestUtils.setField(limiter, "maxInvitations", 100);
        ReflectionTestUtils.setField(limiter, "maxPerStore", 100);
        ReflectionTestUtils.setField(limiter, "maxPerRecipient", 100);

        for (long id = 1; id <= 20; id++) {
            limiter.check(id, id, "staff-" + id + "@coffee.vn");
        }

        @SuppressWarnings("unchecked")
        Map<String, ?> counters = (Map<String, ?>) ReflectionTestUtils.getField(limiter, "fallbackCounters");
        assertThat(counters).hasSizeLessThanOrEqualTo(3);
    }

    private StaffInviteRateLimiter limiter(StringRedisTemplate redisTemplate) {
        StaffInviteRateLimiter limiter = new StaffInviteRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "maxInvitations", 5);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 60L);
        ReflectionTestUtils.setField(limiter, "maxPerStore", 20);
        ReflectionTestUtils.setField(limiter, "maxPerRecipient", 3);
        ReflectionTestUtils.setField(limiter, "recipientWindowMinutes", 1440L);
        ReflectionTestUtils.setField(limiter, "fallbackMaxKeys", 10_000);
        return limiter;
    }
}
