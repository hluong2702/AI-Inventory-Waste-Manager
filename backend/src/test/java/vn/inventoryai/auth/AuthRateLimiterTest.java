package vn.inventoryai.auth;

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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthRateLimiterTest {
    @Test
    void loginUsesAtomicCountersWithoutPuttingIpOrEmailInRedisKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        AuthRateLimiter limiter = limiter(redis);

        limiter.checkLogin("192.0.2.10", "Owner@Example.Test");

        @SuppressWarnings("unchecked")
        var keys = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis, org.mockito.Mockito.times(3))
                .execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertThat(keys.getAllValues().toString())
                .contains("login:ip:", "login:account:", "login:pair:")
                .doesNotContain("192.0.2.10", "owner@example.test", "Owner@Example.Test");
    }

    @Test
    void rejectsBeforeAuthenticationWhenAnyLoginDimensionExceedsLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(999L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        AuthRateLimiter limiter = limiter(redis);

        assertThatThrownBy(() -> limiter.checkLogin("192.0.2.10", "owner@example.test"))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getMessage()).doesNotContain("owner@example.test", "192.0.2.10");
                });
    }

    @Test
    void boundedFallbackStillLimitsWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        AuthRateLimiter limiter = limiter(redis);
        ReflectionTestUtils.setField(limiter, "registerIpMax", 2);
        ReflectionTestUtils.setField(limiter, "registerEmailMax", 100);

        limiter.checkRegister("192.0.2.10", "one@example.test");
        limiter.checkRegister("192.0.2.10", "two@example.test");

        assertThatThrownBy(() -> limiter.checkRegister("192.0.2.10", "three@example.test"))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void fallbackMapEvictsOldestKeysAtConfiguredCapacity() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("redis down"))
                .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        AuthRateLimiter limiter = limiter(redis);
        ReflectionTestUtils.setField(limiter, "fallbackMaxKeys", 3);

        for (int i = 0; i < 20; i++) {
            limiter.checkRefresh("192.0.2." + i, "family.session.secret-" + i);
        }

        @SuppressWarnings("unchecked")
        Map<String, ?> fallback = (Map<String, ?>) ReflectionTestUtils.getField(limiter, "fallbackCounters");
        assertThat(fallback).hasSizeLessThanOrEqualTo(3);
    }

    private AuthRateLimiter limiter(StringRedisTemplate redis) {
        AuthRateLimiter limiter = new AuthRateLimiter(redis);
        ReflectionTestUtils.setField(limiter, "loginIpMax", 50);
        ReflectionTestUtils.setField(limiter, "loginAccountMax", 20);
        ReflectionTestUtils.setField(limiter, "loginPairMax", 8);
        ReflectionTestUtils.setField(limiter, "loginWindowMinutes", 15L);
        ReflectionTestUtils.setField(limiter, "registerIpMax", 10);
        ReflectionTestUtils.setField(limiter, "registerEmailMax", 3);
        ReflectionTestUtils.setField(limiter, "registerWindowMinutes", 60L);
        ReflectionTestUtils.setField(limiter, "registerEmailWindowMinutes", 1440L);
        ReflectionTestUtils.setField(limiter, "refreshIpMax", 120);
        ReflectionTestUtils.setField(limiter, "refreshSessionMax", 20);
        ReflectionTestUtils.setField(limiter, "refreshWindowMinutes", 15L);
        ReflectionTestUtils.setField(limiter, "fallbackMaxKeys", 20_000);
        return limiter;
    }
}
