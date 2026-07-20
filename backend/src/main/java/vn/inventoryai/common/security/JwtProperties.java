package vn.inventoryai.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays
) {
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final double MINIMUM_ENTROPY_BITS_PER_BYTE = 3.5d;

    public JwtProperties {
        requireText(issuer, "app.jwt.issuer");
        requireText(audience, "app.jwt.audience");
        validateSecret(secret);
        if (accessTokenMinutes < 1 || accessTokenMinutes > 15) {
            throw new IllegalArgumentException("app.jwt.access-token-minutes must be between 1 and 15");
        }
        if (refreshTokenDays < 1 || refreshTokenDays > 30) {
            throw new IllegalArgumentException("app.jwt.refresh-token-days must be between 1 and 30");
        }
    }

    private static void validateSecret(String secret) {
        requireText(secret, "app.jwt.secret");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("app.jwt.secret must contain at least 32 UTF-8 bytes");
        }

        String normalized = secret.toLowerCase(Locale.ROOT);
        if (normalized.contains("replace")
                || normalized.contains("changeme")
                || normalized.contains("change-me")
                || normalized.contains("placeholder")
                || normalized.contains("your-secret")
                || normalized.contains("default-secret")
                || normalized.contains("test-secret")
                || normalized.contains("demo-secret")) {
            throw new IllegalArgumentException("app.jwt.secret must not be a placeholder or shared example value");
        }

        int[] frequencies = new int[256];
        for (byte value : bytes) {
            frequencies[value & 0xff]++;
        }
        double entropy = 0d;
        for (int frequency : frequencies) {
            if (frequency == 0) continue;
            double probability = (double) frequency / bytes.length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        if (entropy < MINIMUM_ENTROPY_BITS_PER_BYTE) {
            throw new IllegalArgumentException("app.jwt.secret has insufficient character diversity");
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " is required");
        }
    }
}
