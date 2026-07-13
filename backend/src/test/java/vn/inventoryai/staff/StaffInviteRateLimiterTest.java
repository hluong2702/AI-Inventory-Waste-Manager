package vn.inventoryai.staff;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import vn.inventoryai.common.error.AppException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffInviteRateLimiterTest {
    @Test
    void fallsBackToInMemoryCounterWhenRedisUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("staff_invite:7:10")).thenThrow(new RuntimeException("redis down"));

        StaffInviteRateLimiter limiter = new StaffInviteRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "maxInvitations", 5);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 60L);

        for (int i = 0; i < 5; i++) {
            limiter.check(7L, 10L);
        }

        assertThatThrownBy(() -> limiter.check(7L, 10L))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
