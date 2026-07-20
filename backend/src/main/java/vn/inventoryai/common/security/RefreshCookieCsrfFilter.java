package vn.inventoryai.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.inventoryai.auth.RefreshTokenCookieService;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefreshCookieCsrfFilter extends OncePerRequestFilter {
    private final RefreshTokenCookieService refreshCookieService;
    private final CorsProperties corsProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (!path.equals("/api/auth/refresh") && !path.equals("/api/auth/logout")) return true;
        return !refreshCookieService.isPresent(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !corsProperties.allowedOrigins().contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"code":"FORBIDDEN","message":"Refresh cookie origin is not allowed","timestamp":"%s"}
                    """.formatted(Instant.now()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
