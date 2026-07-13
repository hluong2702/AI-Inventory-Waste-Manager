package vn.inventoryai.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {
    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, ttl);
    }

    public void blacklist(String accessToken, Duration ttl) {
        redisTemplate.opsForValue().set("blacklist:" + accessToken, "1", ttl);
    }
}
