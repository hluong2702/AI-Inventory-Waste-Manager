package vn.inventoryai.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.store.StoreRepository;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final StoreRepository storeRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                UserPrincipal principal;
                try {
                    principal = jwtUtil.parse(header.substring(7));
                } catch (RuntimeException ex) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("""
                            {"code":"UNAUTHORIZED","message":"JWT is invalid or expired","timestamp":"%s"}
                            """.formatted(Instant.now()));
                    return;
                }
                Long selectedStoreId;
                try {
                    selectedStoreId = selectedStoreId(request, principal);
                } catch (IOException ex) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("""
                            {"code":"FORBIDDEN","message":"%s","timestamp":"%s"}
                            """.formatted(ex.getMessage(), Instant.now()));
                    return;
                }
                TenantContext.setStoreId(selectedStoreId);
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

    private Long selectedStoreId(HttpServletRequest request, UserPrincipal principal) throws IOException {
        String headerStoreId = request.getHeader("x-store-id");
        if (headerStoreId == null || headerStoreId.isBlank()) {
            return principal.storeId();
        }
        Long requestedStoreId;
        try {
            requestedStoreId = Long.parseLong(headerStoreId);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid x-store-id header");
        }
        if (principal.role() == Role.SYSTEM_ADMIN || requestedStoreId.equals(principal.storeId())) {
            return requestedStoreId;
        }
        boolean ownsStore = storeRepository.findById(requestedStoreId)
                .map(store -> store.getOwner() != null && store.getOwner().getId().equals(principal.userId()))
                .orElse(false);
        if (ownsStore) {
            return requestedStoreId;
        }
        throw new IOException("Store access denied");
    }
}
