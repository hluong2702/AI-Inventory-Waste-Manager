package vn.inventoryai.common.security;

import org.junit.jupiter.api.Test;
import vn.inventoryai.auth.AppUser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {
    private static final String STRONG_SECRET = "Y7b9wQ2mX4kN8pR6tV3cF5hJ1sD0zL7eA9gC2uK4";

    @Test
    void accessTokenContainsIdentityAndRevocationMetadataWithoutRoleOrTenantClaims() {
        JwtUtil jwtUtil = new JwtUtil(new JwtProperties(
                "inventoryai.vn",
                "inventoryai-api",
                STRONG_SECRET,
                15,
                14
        ));
        AppUser user = new AppUser();
        user.setId(42L);

        String token = jwtUtil.createAccessToken(user);
        JwtUtil.JwtIdentity identity = jwtUtil.parseIdentity(token);

        assertThat(identity.userId()).isEqualTo(42L);
        assertThat(identity.jwtId()).isNotBlank();
        assertThat(identity.remainingLifetime()).isPositive();
        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );
        assertThat(payload).doesNotContain("SYSTEM_ADMIN", "storeId", "\"role\"", "email");
    }
}
