package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffInviteRateLimiter {
    private static final String KEY_PREFIX = "inventory-ai:v2:staff-invite:";
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local exceeded = 0
            for index, key in ipairs(KEYS) do
                local argIndex = ((index - 1) * 2) + 1
                local count = redis.call('INCR', key)
                if count == 1 or redis.call('PTTL', key) < 0 then
                    redis.call('PEXPIRE', key, ARGV[argIndex])
                end
                if count > tonumber(ARGV[argIndex + 1]) and exceeded == 0 then
                    exceeded = index
                end
            end
            return exceeded
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Object fallbackLock = new Object();
    private final Map<String, InMemoryCounter> fallbackCounters = new LinkedHashMap<>(64, 0.75f, true);

    @Value("${app.staff-invite.rate-limit.max:5}")
    private int maxInvitations;

    @Value("${app.staff-invite.rate-limit.window-minutes:60}")
    private long windowMinutes;

    @Value("${app.staff-invite.rate-limit.store-max:20}")
    private int maxPerStore;

    @Value("${app.staff-invite.rate-limit.recipient-max:3}")
    private int maxPerRecipient;

    @Value("${app.staff-invite.rate-limit.recipient-window-minutes:1440}")
    private long recipientWindowMinutes;

    @Value("${app.staff-invite.rate-limit.fallback-max-keys:10000}")
    private int fallbackMaxKeys;

    /**
     * Counts only invitations which passed validation and quota checks and are about to be
     * persisted. Request-level abuse protection belongs at the public/security boundary.
     */
    public void check(Long inviterUserId, Long storeId, String recipientEmail) {
        Duration actorWindow = Duration.ofMinutes(Math.max(windowMinutes, 1));
        Duration recipientWindow = Duration.ofMinutes(Math.max(recipientWindowMinutes, 1));
        String recipientHash = sha256(recipientEmail.trim().toLowerCase(Locale.ROOT)).substring(0, 24);
        List<Limit> limits = List.of(
                new Limit(KEY_PREFIX + "actor:" + inviterUserId + ":store:" + storeId,
                        Math.max(maxInvitations, 1), actorWindow),
                new Limit(KEY_PREFIX + "store:" + storeId,
                        Math.max(maxPerStore, 1), actorWindow),
                new Limit(KEY_PREFIX + "recipient:" + recipientHash,
                        Math.max(maxPerRecipient, 1), recipientWindow)
        );

        try {
            long exceededIndex = executeAtomically(limits);
            if (exceededIndex > 0) {
                throw limitExceeded();
            }
            return;
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable for staff invitation rate limiting; using bounded local fallback ({})",
                    ex.getClass().getSimpleName());
        }

        checkBoundedFallback(limits);
    }

    private long executeAtomically(List<Limit> limits) {
        List<String> keys = limits.stream().map(Limit::key).toList();
        List<String> args = new ArrayList<>(limits.size() * 2);
        for (Limit limit : limits) {
            args.add(Long.toString(limit.ttl().toMillis()));
            args.add(Integer.toString(limit.maxAttempts()));
        }
        Long result = redisTemplate.execute(RATE_LIMIT_SCRIPT, keys, args.toArray());
        if (result == null) {
            throw new IllegalStateException("Redis rate limit script returned no result");
        }
        return result;
    }

    private void checkBoundedFallback(List<Limit> limits) {
        Instant now = Instant.now();
        boolean exceeded = false;
        synchronized (fallbackLock) {
            fallbackCounters.entrySet().removeIf(entry -> !entry.getValue().resetAt().isAfter(now));
            for (Limit limit : limits) {
                InMemoryCounter current = fallbackCounters.get(limit.key());
                int nextCount = current == null || !current.resetAt().isAfter(now)
                        ? 1
                        : current.count() + 1;
                putBounded(limit.key(), new InMemoryCounter(nextCount, now.plus(limit.ttl())));
                exceeded |= nextCount > limit.maxAttempts();
            }
        }
        if (exceeded) {
            throw limitExceeded();
        }
    }

    private void putBounded(String key, InMemoryCounter counter) {
        int capacity = Math.max(fallbackMaxKeys, 3);
        if (!fallbackCounters.containsKey(key) && fallbackCounters.size() >= capacity) {
            Iterator<String> iterator = fallbackCounters.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        fallbackCounters.put(key, counter);
    }

    private AppException limitExceeded() {
        return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.TOO_MANY_REQUESTS,
                "Invite rate limit exceeded. Please try again later.");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record Limit(String key, int maxAttempts, Duration ttl) {
    }

    private record InMemoryCounter(int count, Instant resetAt) {
    }
}
