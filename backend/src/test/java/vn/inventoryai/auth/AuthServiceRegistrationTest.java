package vn.inventoryai.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.inventoryai.auth.dto.RegisterRequest;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.security.JwtProperties;
import vn.inventoryai.common.security.JwtUtil;
import vn.inventoryai.staff.OwnerActivationService;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.SubscriptionPlanEntity;
import vn.inventoryai.subscription.SubscriptionPlanRepository;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegistrationTest {
    @Mock UserRepository userRepository;
    @Mock StoreRepository storeRepository;
    @Mock SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock TenantMembershipRepository membershipRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock JwtProperties jwtProperties;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock OwnerActivationService ownerActivationService;

    @Test
    void registrationRequiresMailboxActivationBeforeIssuingSession() {
        SubscriptionPlanEntity freePlan = new SubscriptionPlanEntity();
        freePlan.setCode(SubscriptionPlan.FREE);
        when(userRepository.existsByEmail("owner@example.com")).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(10L);
            return store;
        });
        when(subscriptionPlanRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE))
                .thenReturn(Optional.of(freePlan));
        when(passwordEncoder.encode("strong-password")).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(20L);
            return user;
        });
        when(membershipRepository.save(any(TenantMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().register(new RegisterRequest("Coffee A", " Owner@Example.com ", "strong-password"));

        assertThat(response.verificationRequired()).isTrue();
        assertThat(response.email()).isEqualTo("owner@example.com");
        ArgumentCaptor<AppUser> owner = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(owner.capture());
        assertThat(owner.getValue().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(owner.getValue().getRole()).isEqualTo(Role.OWNER);
        ArgumentCaptor<TenantMembership> membership = ArgumentCaptor.forClass(TenantMembership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        verify(ownerActivationService).enqueue(
                owner.getValue(),
                owner.getValue().getStore(),
                membership.getValue()
        );
        verifyNoInteractions(jwtUtil, refreshTokenStore);
    }

    private AuthService service() {
        return new AuthService(
                userRepository,
                storeRepository,
                subscriptionPlanRepository,
                tenantSubscriptionRepository,
                membershipRepository,
                passwordEncoder,
                jwtUtil,
                jwtProperties,
                refreshTokenStore,
                ownerActivationService,
                Clock.systemUTC()
        );
    }
}
