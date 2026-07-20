package vn.inventoryai.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.dto.*;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.JwtProperties;
import vn.inventoryai.common.security.JwtUtil;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.SubscriptionPlanRepository;
import vn.inventoryai.subscription.SubscriptionStatus;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;
import vn.inventoryai.staff.OwnerActivationService;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final OwnerActivationService ownerActivationService;
    private final Clock clock;

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT, "Email already exists");
        }

        Store store = new Store();
        store.setName(request.storeName().trim());
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);
        store = storeRepository.save(store);

        LocalDate businessDate = LocalDate.now(clock);
        var freePlan = subscriptionPlanRepository.findByCodeAndActiveTrue(SubscriptionPlan.FREE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Free subscription plan not found"));
        TenantSubscription tenantSubscription = new TenantSubscription();
        tenantSubscription.setTenant(store);
        tenantSubscription.setPlan(freePlan);
        tenantSubscription.setStatus(SubscriptionStatus.ACTIVE);
        tenantSubscription.setStartDate(businessDate);
        tenantSubscription.setEndDate(businessDate.plusMonths(1));
        tenantSubscription.setAutoRenew(false);
        tenantSubscription.setActivatedAt(Instant.now());
        tenantSubscriptionRepository.save(tenantSubscription);

        AppUser owner = new AppUser();
        owner.setStore(store);
        owner.setFullName("Store Owner");
        owner.setEmail(email);
        owner.setPasswordHash(passwordEncoder.encode(request.password()));
        owner.setRole(Role.OWNER);
        owner.setStatus(UserStatus.PENDING_ACTIVATION);
        owner.setMustChangePassword(false);
        owner = userRepository.save(owner);

        store.setOwner(owner);
        storeRepository.save(store);

        TenantMembership membership = new TenantMembership();
        membership.setStore(store);
        membership.setUser(owner);
        membership.setRole(Role.OWNER);
        membership.setStatus(UserStatus.PENDING_ACTIVATION);
        membership = membershipRepository.save(membership);

        ownerActivationService.enqueue(owner, store, membership);
        return new RegistrationResponse(true, owner.getEmail(), 48);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_DISABLED, HttpStatus.FORBIDDEN, "User is not active");
        }

        return tokenResponse(user, defaultActiveMembership(user), null, null);
    }

    @Transactional
    public AuthResponse firstLoginChangePassword(FirstLoginChangePasswordRequest request) {
        Long userId = SecurityUtils.principal().userId();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));

        if (!user.isMustChangePassword()) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "User is not required to change password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user = userRepository.save(user);
        return tokenResponse(user, selectedActiveMembership(user), null, null);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenStore.ConsumeResult consumed = refreshTokenStore.consume(refreshToken);
        if (consumed.status() != RefreshTokenStore.ConsumeResult.Status.VALID) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
        AppUser user = userRepository.findById(consumed.userId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_DISABLED, HttpStatus.FORBIDDEN, "User is not active");
        }
        return tokenResponse(
                user,
                defaultActiveMembership(user),
                consumed.familyId(),
                consumed.remainingLifetime()
        );
    }

    public void logout(Long userId, String refreshToken, String accessToken) {
        if (refreshToken != null) refreshTokenStore.revoke(userId, refreshToken);
        if (accessToken != null) {
            JwtUtil.JwtIdentity identity = jwtUtil.parseIdentity(accessToken);
            if (!identity.userId().equals(userId)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Access token does not belong to user");
            }
            refreshTokenStore.denyAccessToken(identity.jwtId(), identity.remainingLifetime());
        }
    }

    private AuthResponse tokenResponse(AppUser user,
                                       TenantMembership membership,
                                       String refreshFamilyId,
                                       Duration remainingRefreshLifetime) {
        String accessToken = jwtUtil.createAccessToken(user);
        boolean rotating = refreshFamilyId != null;
        String refreshToken = rotating
                ? refreshTokenStore.rotateToken(refreshFamilyId)
                : refreshTokenStore.newToken();
        Duration refreshTtl = rotating
                ? remainingRefreshLifetime
                : Duration.ofDays(jwtProperties.refreshTokenDays());
        if (refreshTtl == null || refreshTtl.isNegative() || refreshTtl.isZero()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token is expired");
        }
        boolean saved;
        if (!rotating) {
            refreshTokenStore.startSession(user.getId(), refreshToken, refreshTtl);
            saved = true;
        } else {
            saved = refreshTokenStore.rotateSession(user.getId(), refreshToken, refreshTtl);
        }
        if (!saved) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token family has been revoked");
        }
        Long storeId = membership == null ? null : membership.getStore().getId();
        Role role = membership == null ? user.getRole() : membership.getRole();
        return new AuthResponse(accessToken, refreshToken, user.getId(), storeId, role, user.isMustChangePassword());
    }

    private TenantMembership selectedActiveMembership(AppUser user) {
        if (user.getRole() == Role.SYSTEM_ADMIN) return null;
        Long selectedStoreId = SecurityUtils.storeId();
        if (selectedStoreId == null) return defaultActiveMembership(user);
        return membershipRepository.findByUserIdAndStoreIdAndStatusAndStoreStatus(
                        user.getId(), selectedStoreId, UserStatus.ACTIVE, StoreStatus.ACTIVE
                )
                .orElseThrow(() -> noActiveTenant());
    }

    private TenantMembership defaultActiveMembership(AppUser user) {
        if (user.getRole() == Role.SYSTEM_ADMIN) return null;
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
                .orElseThrow(this::noActiveTenant);
    }

    private AppException noActiveTenant() {
        return new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "No active tenant membership is available");
    }
}
