package vn.inventoryai.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.inventoryai.auth.dto.AuthResponse;
import vn.inventoryai.auth.dto.LoginRequest;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.security.ClientIpResolver;
import vn.inventoryai.staff.InvitationMailConfigurationGuard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void loginWritesRefreshCookieButNeverReturnsRawRefreshTokenInBody() {
        AuthService authService = mock(AuthService.class);
        RefreshTokenCookieService cookies = mock(RefreshTokenCookieService.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        InvitationMailConfigurationGuard mailConfiguration = mock(InvitationMailConfigurationGuard.class);
        AuthController controller = new AuthController(
                authService, cookies, rateLimiter, clientIpResolver, mailConfiguration
        );
        LoginRequest request = new LoginRequest("owner@example.test", "correct-password");
        AuthResponse internal = new AuthResponse("access", "raw-refresh", 7L, 11L, Role.OWNER, false);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.0.2.10");
        when(clientIpResolver.resolve(httpRequest)).thenReturn("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.login(request)).thenReturn(internal);

        AuthResponse published = controller.login(request, httpRequest, response);

        assertThat(published.accessToken()).isEqualTo("access");
        assertThat(published.refreshToken()).isNull();
        verify(rateLimiter).checkLogin("192.0.2.10", "owner@example.test");
        verify(cookies).write(response, "raw-refresh");
    }

    @Test
    void refreshUsesOnlyHttpOnlyCookie() {
        AuthService authService = mock(AuthService.class);
        RefreshTokenCookieService cookies = mock(RefreshTokenCookieService.class);
        AuthRateLimiter rateLimiter = mock(AuthRateLimiter.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        InvitationMailConfigurationGuard mailConfiguration = mock(InvitationMailConfigurationGuard.class);
        AuthController controller = new AuthController(
                authService, cookies, rateLimiter, clientIpResolver, mailConfiguration
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cookies.read(request)).thenReturn("cookie-token");
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");
        when(authService.refresh("cookie-token"))
                .thenReturn(new AuthResponse("new-access", "new-refresh", 7L, 11L, Role.OWNER, false));

        AuthResponse published = controller.refresh(request, response);

        assertThat(published.refreshToken()).isNull();
        verify(rateLimiter).checkRefresh("127.0.0.1", "cookie-token");
        verify(authService).refresh("cookie-token");
        verify(cookies).write(response, "new-refresh");
    }
}
