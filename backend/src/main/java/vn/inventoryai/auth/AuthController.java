package vn.inventoryai.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.ClientIpResolver;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.auth.dto.*;
import vn.inventoryai.staff.InvitationMailConfigurationGuard;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookieService refreshCookieService;
    private final AuthRateLimiter authRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final InvitationMailConfigurationGuard invitationMailConfiguration;

    @PostMapping("/register")
    RegistrationResponse register(@Valid @RequestBody RegisterRequest request,
                                  HttpServletRequest httpRequest) {
        invitationMailConfiguration.assertDeliveryAvailable();
        authRateLimiter.checkRegister(clientIpResolver.resolve(httpRequest), request.email());
        return authService.register(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request,
                       HttpServletRequest httpRequest,
                       HttpServletResponse response) {
        authRateLimiter.checkLogin(clientIpResolver.resolve(httpRequest), request.email());
        return publish(authService.login(request), response);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(HttpServletRequest request,
                         HttpServletResponse response) {
        String refreshToken = refreshCookieService.read(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        authRateLimiter.checkRefresh(clientIpResolver.resolve(request), refreshToken);
        try {
            return publish(authService.refresh(refreshToken), response);
        } catch (AppException ex) {
            refreshCookieService.clear(response);
            throw ex;
        }
    }

    @PostMapping("/logout")
    void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                HttpServletRequest httpRequest,
                HttpServletResponse response) {
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        String refreshToken = refreshCookieService.read(httpRequest);
        authService.logout(SecurityUtils.principal().userId(), refreshToken, authorization.substring(7));
        refreshCookieService.clear(response);
    }

    @PostMapping("/first-login-change-password")
    AuthResponse firstLoginChangePassword(@Valid @RequestBody FirstLoginChangePasswordRequest request,
                                          HttpServletResponse response) {
        return publish(authService.firstLoginChangePassword(request), response);
    }

    private AuthResponse publish(AuthResponse auth, HttpServletResponse response) {
        refreshCookieService.write(response, auth.refreshToken());
        return new AuthResponse(
                auth.accessToken(),
                null,
                auth.userId(),
                auth.storeId(),
                auth.role(),
                auth.mustChangePassword()
        );
    }
}
