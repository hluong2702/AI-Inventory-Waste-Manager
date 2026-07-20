package vn.inventoryai.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.inventoryai.common.security.AuthCookieProperties;
import vn.inventoryai.common.security.JwtProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieServiceTest {
    private static final String STRONG_SECRET = "Y7b9wQ2mX4kN8pR6tV3cF5hJ1sD0zL7eA9gC2uK4";

    @Test
    void writesHttpOnlySecureSameSiteCookieScopedToAuthEndpoints() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                new AuthCookieProperties("inventoryai_refresh", true, "Lax", ".example.test"),
                new JwtProperties("inventoryai.vn", "inventoryai-api", STRONG_SECRET, 15, 14)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(response, "session.secret");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("inventoryai_refresh=session.secret")
                .contains("Path=/api/auth")
                .contains("Domain=.example.test")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=");
    }

    @Test
    void clearExpiresCookieImmediately() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(
                new AuthCookieProperties("inventoryai_refresh", true, "Lax", ""),
                new JwtProperties("inventoryai.vn", "inventoryai-api", STRONG_SECRET, 15, 14)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }
}
