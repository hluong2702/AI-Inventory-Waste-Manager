package vn.inventoryai.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.inventoryai.auth.RefreshTokenCookieService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshCookieCsrfFilterTest {
    @Test
    void rejectsRefreshCookieFromUntrustedOrigin() throws Exception {
        RefreshTokenCookieService cookies = mock(RefreshTokenCookieService.class);
        CorsProperties cors = new CorsProperties(List.of("https://app.example.test"));
        RefreshCookieCsrfFilter filter = new RefreshCookieCsrfFilter(cookies, cors);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie("inventoryai_refresh", "token"));
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookies.isPresent(request)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void permitsRefreshCookieOnlyFromConfiguredOrigin() throws Exception {
        RefreshTokenCookieService cookies = mock(RefreshTokenCookieService.class);
        CorsProperties cors = new CorsProperties(List.of("https://app.example.test"));
        RefreshCookieCsrfFilter filter = new RefreshCookieCsrfFilter(cookies, cors);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie("inventoryai_refresh", "token"));
        request.addHeader("Origin", "https://app.example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookies.isPresent(request)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
