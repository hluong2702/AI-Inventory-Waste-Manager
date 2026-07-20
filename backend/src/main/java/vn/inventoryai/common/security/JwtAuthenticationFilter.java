package vn.inventoryai.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.RefreshTokenStore;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.UserStatus;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String TENANT_HEADER = "x-store-id";

    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                UserPrincipal principal;
                try {
                    JwtUtil.JwtIdentity identity = jwtUtil.parseIdentity(header.substring(7));
                    if (refreshTokenStore.isAccessTokenDenied(identity.jwtId())) {
                        throw new IllegalArgumentException("Access token has been revoked");
                    }
                    AppUser user = userRepository.findById(identity.userId())
                            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                            .orElseThrow(() -> new IllegalArgumentException("User is inactive"));
                    principal = authoritativePrincipal(request, user);
                } catch (TenantAccessDeniedException ex) {
                    SecurityContextHolder.clearContext();
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", ex.getMessage());
                    return;
                } catch (RuntimeException ex) {
                    SecurityContextHolder.clearContext();
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "JWT is invalid or expired");
                    return;
                }

                if (principal.storeId() != null) {
                    TenantContext.setStoreId(principal.storeId());
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UserPrincipal authoritativePrincipal(HttpServletRequest request, AppUser user) {
        String requestedStore = request.getHeader(TENANT_HEADER);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            if (requestedStore != null && !requestedStore.isBlank()) {
                throw new TenantAccessDeniedException(
                        "System administrators cannot impersonate tenants with x-store-id; use an audited support session"
                );
            }
            return new UserPrincipal(user.getId(), null, user.getEmail(), Role.SYSTEM_ADMIN, user.isMustChangePassword());
        }

        TenantMembership membership = requestedStore == null || requestedStore.isBlank()
                ? defaultMembership(user)
                : requestedMembership(user.getId(), requestedStore);
        return new UserPrincipal(
                user.getId(),
                membership.getStore().getId(),
                user.getEmail(),
                membership.getRole(),
                user.isMustChangePassword()
        );
    }

    private TenantMembership defaultMembership(AppUser user) {
        Long preferredStoreId = user.getStore() == null ? null : user.getStore().getId();
        if (preferredStoreId != null) {
            var preferred = membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                    user.getId(), preferredStoreId, UserStatus.ACTIVE, StoreStatus.ACTIVE
            );
            if (preferred.isPresent()) return preferred.get();
        }
        return membershipRepository.findFirstByUserIdAndStatusAndStoreStatusOrderByIdAsc(
                        user.getId(), UserStatus.ACTIVE, StoreStatus.ACTIVE
                )
                .orElseThrow(() -> new TenantAccessDeniedException("No active tenant membership is available"));
    }

    private TenantMembership requestedMembership(Long userId, String requestedStore) {
        Long storeId;
        try {
            storeId = Long.valueOf(requestedStore);
        } catch (NumberFormatException ex) {
            throw new TenantAccessDeniedException("Invalid x-store-id header");
        }
        return membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                        userId, storeId, UserStatus.ACTIVE, StoreStatus.ACTIVE
                )
                .orElseThrow(() -> new TenantAccessDeniedException("Tenant access denied or tenant is suspended"));
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"code":"%s","message":"%s","timestamp":"%s"}
                """.formatted(code, message, Instant.now()));
    }

    private static final class TenantAccessDeniedException extends RuntimeException {
        private TenantAccessDeniedException(String message) {
            super(message);
        }
    }
}
