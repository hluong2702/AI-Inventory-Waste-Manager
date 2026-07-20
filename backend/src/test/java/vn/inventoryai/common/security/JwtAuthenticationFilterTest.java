package vn.inventoryai.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.RefreshTokenStore;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.store.Store;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void tokenCannotSelectTenantWithoutActiveMembership() throws Exception {
        Fixture fixture = new Fixture();
        AppUser user = activeUser(101L, Role.OWNER, store(11L, StoreStatus.ACTIVE));
        fixture.validToken("account-a-token", 101L, user);
        when(fixture.memberships.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                101L, 22L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.empty());

        MockHttpServletRequest request = authorizedRequest("account-a-token");
        request.addHeader("x-store-id", "22");
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request, response, fixture.chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Tenant access denied");
        verify(fixture.chain, never()).doFilter(request, response);
    }

    @Test
    void principalUsesTenantMembershipRoleInsteadOfTokenOrGlobalRole() throws Exception {
        Fixture fixture = new Fixture();
        Store store = store(11L, StoreStatus.ACTIVE);
        AppUser user = activeUser(101L, Role.OWNER, store);
        TenantMembership membership = membership(user, store, Role.STAFF);
        fixture.validToken("token", 101L, user);
        when(fixture.memberships.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                101L, 11L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.of(membership));
        AtomicReference<UserPrincipal> authenticated = new AtomicReference<>();
        FilterChain captureChain = (request, response) -> authenticated.set(SecurityUtils.principal());

        fixture.filter.doFilter(authorizedRequest("token"), new MockHttpServletResponse(), captureChain);

        assertThat(authenticated.get().role()).isEqualTo(Role.STAFF);
        assertThat(authenticated.get().storeId()).isEqualTo(11L);
    }

    @Test
    void suspendedTenantIsRejectedEvenWhenItIsUsersLegacyStore() throws Exception {
        Fixture fixture = new Fixture();
        AppUser user = activeUser(101L, Role.OWNER, store(11L, StoreStatus.SUSPENDED));
        fixture.validToken("token", 101L, user);
        when(fixture.memberships.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                101L, 11L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(fixture.memberships.findFirstByUserIdAndStatusAndStoreStatusOrderByIdAsc(
                101L, UserStatus.ACTIVE, StoreStatus.ACTIVE
        )).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(authorizedRequest("token"), response, fixture.chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(fixture.chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void systemAdminCannotSwitchTenantWithRawHeader() throws Exception {
        Fixture fixture = new Fixture();
        AppUser user = activeUser(1L, Role.SYSTEM_ADMIN, null);
        fixture.validToken("admin-token", 1L, user);
        MockHttpServletRequest request = authorizedRequest("admin-token");
        request.addHeader("x-store-id", "22");
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request, response, fixture.chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("audited support session");
        verify(fixture.chain, never()).doFilter(request, response);
    }

    private static MockHttpServletRequest authorizedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static Store store(Long id, StoreStatus status) {
        Store store = new Store();
        store.setId(id);
        store.setStatus(status);
        return store;
    }

    private static AppUser activeUser(Long id, Role role, Store store) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail("user-" + id + "@example.test");
        user.setRole(role);
        user.setStore(store);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static TenantMembership membership(AppUser user, Store store, Role role) {
        TenantMembership membership = new TenantMembership();
        membership.setUser(user);
        membership.setStore(store);
        membership.setRole(role);
        membership.setStatus(UserStatus.ACTIVE);
        return membership;
    }

    private static final class Fixture {
        private final JwtUtil jwtUtil = mock(JwtUtil.class);
        private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
        private final UserRepository users = mock(UserRepository.class);
        private final TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        private final FilterChain chain = mock(FilterChain.class);
        private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, refreshTokens, users, memberships);

        private void validToken(String token, Long userId, AppUser user) {
            when(jwtUtil.parseIdentity(token)).thenReturn(new JwtUtil.JwtIdentity(userId, "jti-" + userId, Duration.ofMinutes(5)));
            when(refreshTokens.isAccessTokenDenied("jti-" + userId)).thenReturn(false);
            when(users.findById(userId)).thenReturn(Optional.of(user));
        }
    }
}
