package vn.inventoryai.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {
    private static final String STRONG_SECRET = "Y7b9wQ2mX4kN8pR6tV3cF5hJ1sD0zL7eA9gC2uK4";

    @Test
    void rejectsKnownPlaceholderSecret() {
        assertThatThrownBy(() -> new JwtProperties(
                "inventoryai.vn",
                "inventoryai-api",
                "replace-with-at-least-32-bytes-production-secret",
                15,
                14
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void rejectsLowDiversitySecret() {
        assertThatThrownBy(() -> new JwtProperties(
                "inventoryai.vn",
                "inventoryai-api",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                15,
                14
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diversity");
    }

    @Test
    void rejectsAccessTokensLongerThanFifteenMinutes() {
        assertThatThrownBy(() -> new JwtProperties(
                "inventoryai.vn",
                "inventoryai-api",
                STRONG_SECRET,
                30,
                14
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 15");
    }
}
