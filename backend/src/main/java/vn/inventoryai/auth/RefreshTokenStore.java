package vn.inventoryai.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PREFIX = "inventoryai:v3:auth:";
    private static final String ACCESS_DENYLIST_PREFIX = PREFIX + "access:denylist:";

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            if ARGV[7] == '1' then
                redis.call('DEL', KEYS[3])
            elseif redis.call('EXISTS', KEYS[3]) == 1 then
                return 0
            end
            local previous = redis.call('GET', KEYS[1])
            if previous then
                redis.call('DEL', ARGV[6] .. previous)
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[5])
            redis.call('HSET', KEYS[2],
                    'userId', ARGV[2],
                    'familyId', ARGV[3],
                    'tokenHash', ARGV[4])
            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local userId = redis.call('HGET', KEYS[1], 'userId')
            local familyId = redis.call('HGET', KEYS[1], 'familyId')
            local storedHash = redis.call('HGET', KEYS[1], 'tokenHash')
            if not userId or not familyId or not storedHash then
                local reusedUserId = redis.call('HGET', KEYS[2], 'userId')
                local reusedFamilyId = redis.call('HGET', KEYS[2], 'familyId')
                if not reusedUserId or not reusedFamilyId then
                    return 'INVALID'
                end
                local ttl = redis.call('PTTL', KEYS[2])
                if ttl < 1 then ttl = 1000 end
                local current = redis.call('GET', KEYS[3])
                if current then
                    redis.call('DEL', ARGV[2] .. current)
                end
                redis.call('DEL', KEYS[3])
                redis.call('SET', KEYS[4], '1', 'PX', ttl)
                return 'REUSED:' .. reusedUserId .. ':' .. reusedFamilyId .. ':0'
            end
            if storedHash ~= ARGV[1] or familyId ~= ARGV[4] then
                return 'INVALID'
            end
            if redis.call('GET', KEYS[3]) ~= ARGV[3] then
                redis.call('DEL', KEYS[1])
                return 'INVALID'
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 1 then ttl = 1000 end
            redis.call('HSET', KEYS[2], 'userId', userId, 'familyId', familyId)
            redis.call('PEXPIRE', KEYS[2], ttl)
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[3])
            return 'VALID:' .. userId .. ':' .. familyId .. ':' .. ttl
            """, String.class);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            if redis.call('HGET', KEYS[2], 'userId') ~= ARGV[2]
                    or redis.call('HGET', KEYS[2], 'familyId') ~= ARGV[3]
                    or redis.call('HGET', KEYS[2], 'tokenHash') ~= ARGV[4] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public String newToken() {
        return newToken(UUID.randomUUID().toString());
    }

    public String rotateToken(String familyId) {
        validateUuid(familyId);
        return newToken(familyId);
    }

    public void startSession(Long userId, String refreshToken, Duration ttl) {
        save(userId, refreshToken, ttl, true);
    }

    public boolean rotateSession(Long userId, String refreshToken, Duration ttl) {
        return save(userId, refreshToken, ttl, false);
    }

    public ConsumeResult consume(String refreshToken) {
        ParsedToken parsed = parse(refreshToken);
        if (parsed == null) return ConsumeResult.invalid();
        String result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(
                        sessionKey(parsed.familyId(), parsed.sessionId()),
                        usedKey(parsed.familyId(), parsed.sessionId()),
                        currentKey(parsed.familyId()),
                        compromisedKey(parsed.familyId())
                ),
                parsed.tokenHash(),
                sessionPrefix(parsed.familyId()),
                parsed.sessionId(),
                parsed.familyId()
        );
        return ConsumeResult.fromRedis(result);
    }

    public void revoke(Long userId, String refreshToken) {
        ParsedToken parsed = parse(refreshToken);
        if (parsed == null) return;
        redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(currentKey(parsed.familyId()), sessionKey(parsed.familyId(), parsed.sessionId())),
                parsed.sessionId(),
                userId.toString(),
                parsed.familyId(),
                parsed.tokenHash()
        );
    }

    public void denyAccessToken(String jwtId, Duration ttl) {
        if (jwtId == null || jwtId.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) return;
        redisTemplate.opsForValue().set(ACCESS_DENYLIST_PREFIX + jwtId, "1", ttl);
    }

    public boolean isAccessTokenDenied(String jwtId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACCESS_DENYLIST_PREFIX + jwtId));
    }

    private String newToken(String familyId) {
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        return familyId + "." + UUID.randomUUID() + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private boolean save(Long userId, String refreshToken, Duration ttl, boolean resetFamily) {
        ParsedToken parsed = parse(refreshToken);
        if (userId == null || parsed == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Valid refresh token, user ID and TTL are required");
        }
        Long saved = redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(
                        currentKey(parsed.familyId()),
                        sessionKey(parsed.familyId(), parsed.sessionId()),
                        compromisedKey(parsed.familyId())
                ),
                parsed.sessionId(),
                userId.toString(),
                parsed.familyId(),
                parsed.tokenHash(),
                Long.toString(ttl.toMillis()),
                sessionPrefix(parsed.familyId()),
                resetFamily ? "1" : "0"
        );
        return Long.valueOf(1L).equals(saved);
    }

    private ParsedToken parse(String token) {
        if (token == null || token.isBlank() || token.length() > 512) return null;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) return null;
        try {
            validateUuid(parts[0]);
            validateUuid(parts[1]);
            Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return new ParsedToken(parts[0], parts[1], sha256(token));
    }

    private void validateUuid(String value) {
        UUID.fromString(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String familyPrefix(String familyId) {
        return PREFIX + "refresh:{" + familyId + "}:";
    }

    private String currentKey(String familyId) {
        return familyPrefix(familyId) + "current";
    }

    private String sessionPrefix(String familyId) {
        return familyPrefix(familyId) + "session:";
    }

    private String sessionKey(String familyId, String sessionId) {
        return sessionPrefix(familyId) + sessionId;
    }

    private String usedKey(String familyId, String sessionId) {
        return familyPrefix(familyId) + "used:" + sessionId;
    }

    private String compromisedKey(String familyId) {
        return familyPrefix(familyId) + "compromised";
    }

    private record ParsedToken(String familyId, String sessionId, String tokenHash) {
    }

    public record ConsumeResult(Status status, Long userId, String familyId, Duration remainingLifetime) {
        public enum Status {
            VALID,
            INVALID,
            REUSED
        }

        public static ConsumeResult invalid() {
            return new ConsumeResult(Status.INVALID, null, null, null);
        }

        private static ConsumeResult fromRedis(String value) {
            if (value == null || value.equals("INVALID")) return invalid();
            String[] parts = value.split(":", 4);
            if (parts.length != 4) return invalid();
            if (parts[0].equals("VALID")) {
                return new ConsumeResult(
                        Status.VALID,
                        Long.valueOf(parts[1]),
                        parts[2],
                        Duration.ofMillis(Long.parseLong(parts[3]))
                );
            }
            if (parts[0].equals("REUSED")) {
                return new ConsumeResult(Status.REUSED, Long.valueOf(parts[1]), parts[2], null);
            }
            return invalid();
        }
    }
}
