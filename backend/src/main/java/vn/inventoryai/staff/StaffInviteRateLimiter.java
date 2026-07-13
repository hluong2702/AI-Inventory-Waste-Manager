package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffInviteRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final Map<String, InMemoryCounter> fallbackCounters = new ConcurrentHashMap<>();

    @Value("${app.staff-invite.rate-limit.max:5}")
    private int maxInvitations;

    @Value("${app.staff-invite.rate-limit.window-minutes:60}")
    private long windowMinutes;

    public void check(Long inviterUserId, Long storeId) {
        String key = "staff_invite:" + inviterUserId + ":" + storeId;
        Duration ttl = Duration.ofMinutes(windowMinutes);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            if (count != null && count > maxInvitations) {
                throw limitExceeded();
            }
            return;
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable for staff invite rate limit, falling back to memory: {}", ex.getMessage());
        }

        InMemoryCounter counter = fallbackCounters.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || !current.resetAt().isAfter(now)) {
                return new InMemoryCounter(new AtomicInteger(1), now.plus(ttl));
            }
            current.count().incrementAndGet();
            return current;
        });
        if (counter.count().get() > maxInvitations) {
            throw limitExceeded();
        }
    }

    private AppException limitExceeded() {
        return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.TOO_MANY_REQUESTS, "Invite rate limit exceeded. Please try again later.");
    }

    private record InMemoryCounter(AtomicInteger count, Instant resetAt) {
    }
}
