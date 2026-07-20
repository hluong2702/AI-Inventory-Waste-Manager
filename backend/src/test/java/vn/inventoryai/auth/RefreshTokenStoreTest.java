package vn.inventoryai.auth;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RefreshTokenStoreTest {
    @Test
    void refreshTokenContainsOpaqueSessionIdAndHighEntropySecret() {
        RefreshTokenStore store = new RefreshTokenStore(mock(StringRedisTemplate.class));

        String token = store.newToken();
        String[] parts = token.split("\\.");

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).matches("[0-9a-f-]{36}");
        assertThat(parts[1]).matches("[0-9a-f-]{36}");
        assertThat(parts[2]).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void malformedTokenIsRejectedBeforeAnyRedisLookup() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RefreshTokenStore store = new RefreshTokenStore(redis);

        RefreshTokenStore.ConsumeResult result = store.consume("raw-token-without-session-id");

        assertThat(result.status()).isEqualTo(RefreshTokenStore.ConsumeResult.Status.INVALID);
        verifyNoInteractions(redis);
    }
}
