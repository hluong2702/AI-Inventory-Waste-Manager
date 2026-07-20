package vn.inventoryai.auth;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenStoreRedisIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void rotationIsAtomicAndReuseRevokesOnlyTheCompromisedFamily() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            connectionFactory.getConnection().serverCommands().flushDb();
            RefreshTokenStore store = new RefreshTokenStore(redis);

            String original = store.newToken();
            store.startSession(7L, original, Duration.ofMinutes(10));
            RefreshTokenStore.ConsumeResult consumed = store.consume(original);
            assertThat(consumed.status()).isEqualTo(RefreshTokenStore.ConsumeResult.Status.VALID);
            assertThat(consumed.userId()).isEqualTo(7L);

            String rotated = store.rotateToken(consumed.familyId());
            assertThat(store.rotateSession(7L, rotated, consumed.remainingLifetime())).isTrue();
            assertThat(store.consume(original).status()).isEqualTo(RefreshTokenStore.ConsumeResult.Status.REUSED);
            assertThat(store.consume(rotated).status()).isEqualTo(RefreshTokenStore.ConsumeResult.Status.INVALID);

            String freshLogin = store.newToken();
            store.startSession(7L, freshLogin, Duration.ofMinutes(10));
            assertThat(store.consume(original).status()).isEqualTo(RefreshTokenStore.ConsumeResult.Status.REUSED);
            assertThat(store.consume(freshLogin).status()).isEqualTo(RefreshTokenStore.ConsumeResult.Status.VALID);
        } finally {
            connectionFactory.destroy();
        }
    }
}
