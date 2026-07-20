package vn.inventoryai.auth;

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
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {
    private static final String KEY_PREFIX = "inventoryai:v1:auth-rate:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('PTTL', KEYS[1]) < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Object fallbackLock = new Object();
    private final Map<String, InMemoryCounter> fallbackCounters = new LinkedHashMap<>(128, 0.75f, true);

    @Value("${app.auth.rate-limit.login-ip-max:50}")
    private int loginIpMax;

    @Value("${app.auth.rate-limit.login-account-max:20}")
    private int loginAccountMax;

    @Value("${app.auth.rate-limit.login-pair-max:8}")
    private int loginPairMax;

    @Value("${app.auth.rate-limit.login-window-minutes:15}")
    private long loginWindowMinutes;

    @Value("${app.auth.rate-limit.register-ip-max:10}")
    private int registerIpMax;

    @Value("${app.auth.rate-limit.register-email-max:3}")
    private int registerEmailMax;

    @Value("${app.auth.rate-limit.register-window-minutes:60}")
    private long registerWindowMinutes;

    @Value("${app.auth.rate-limit.register-email-window-minutes:1440}")
    private long registerEmailWindowMinutes;

    @Value("${app.auth.rate-limit.refresh-ip-max:120}")
    private int refreshIpMax;

    @Value("${app.auth.rate-limit.refresh-session-max:20}")
    private int refreshSessionMax;

    @Value("${app.auth.rate-limit.refresh-window-minutes:15}")
    private long refreshWindowMinutes;

    @Value("${app.auth.rate-limit.fallback-max-keys:20000}")
    private int fallbackMaxKeys;

    public void checkLogin(String remoteAddress, String email) {
        String ipHash = fingerprint(normalize(remoteAddress));
        String emailHash = fingerprint(normalizeEmail(email));
        Duration window = minutes(loginWindowMinutes);
        check(List.of(
                limit("login:ip:" + ipHash, loginIpMax, window),
                limit("login:account:" + emailHash, loginAccountMax, window),
                limit("login:pair:" + fingerprint(ipHash + ":" + emailHash), loginPairMax, window)
        ));
    }

    public void checkRegister(String remoteAddress, String email) {
        String ipHash = fingerprint(normalize(remoteAddress));
        String emailHash = fingerprint(normalizeEmail(email));
        check(List.of(
                limit("register:ip:" + ipHash, registerIpMax, minutes(registerWindowMinutes)),
                limit("register:email:" + emailHash, registerEmailMax, minutes(registerEmailWindowMinutes))
        ));
    }

    public void checkRefresh(String remoteAddress, String refreshToken) {
        String ipHash = fingerprint(normalize(remoteAddress));
        String sessionHash = fingerprint(normalize(refreshToken));
        Duration window = minutes(refreshWindowMinutes);
        check(List.of(
                limit("refresh:ip:" + ipHash, refreshIpMax, window),
                limit("refresh:session:" + sessionHash, refreshSessionMax, window)
        ));
    }

    private void check(List<Limit> limits) {
        try {
            boolean exceeded = false;
            for (Limit limit : limits) {
                Long count = redisTemplate.execute(
                        INCREMENT_SCRIPT,
                        List.of(limit.key()),
                        Long.toString(limit.ttl().toMillis())
                );
                if (count == null) throw new IllegalStateException("Redis limiter returned no count");
                exceeded |= count > limit.maxAttempts();
            }
            if (exceeded) throw rateLimited();
            return;
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable for auth rate limiting; using bounded local fallback ({})",
                    ex.getClass().getSimpleName());
        }
        checkFallback(limits);
    }

    private void checkFallback(List<Limit> limits) {
        Instant now = Instant.now();
        boolean exceeded = false;
        synchronized (fallbackLock) {
            fallbackCounters.entrySet().removeIf(entry -> !entry.getValue().resetAt().isAfter(now));
            for (Limit limit : limits) {
                InMemoryCounter current = fallbackCounters.get(limit.key());
                int count = current == null || !current.resetAt().isAfter(now) ? 1 : current.count() + 1;
                putBounded(limit.key(), new InMemoryCounter(count, now.plus(limit.ttl())));
                exceeded |= count > limit.maxAttempts();
            }
        }
        if (exceeded) throw rateLimited();
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

    private Limit limit(String suffix, int maxAttempts, Duration ttl) {
        return new Limit(KEY_PREFIX + suffix, Math.max(maxAttempts, 1), ttl);
    }

    private Duration minutes(long value) {
        return Duration.ofMinutes(Math.max(value, 1));
    }

    private String normalizeEmail(String email) {
        return normalize(email).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private AppException rateLimited() {
        return new AppException(
                ErrorCode.RATE_LIMITED,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many authentication attempts. Please try again later."
        );
    }

    private record Limit(String key, int maxAttempts, Duration ttl) {
    }

    private record InMemoryCounter(int count, Instant resetAt) {
    }
}
