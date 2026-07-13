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
import vn.inventoryai.store.Subscription;
import vn.inventoryai.store.SubscriptionRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT, "Email already exists");
        }

        Store store = new Store();
        store.setName(request.storeName());
        store.setSubscriptionPlan(SubscriptionPlan.FREE);
        store.setStatus(StoreStatus.ACTIVE);
        store = storeRepository.save(store);

        Subscription subscription = new Subscription();
        subscription.setStore(store);
        subscription.setPlan(SubscriptionPlan.FREE);
        subscription.setMaxStaff(2);
        subscription.setMaxIngredients(30);
        subscription.setExpiresAt(LocalDate.now().plusMonths(1));
        subscriptionRepository.save(subscription);

        AppUser owner = new AppUser();
        owner.setStore(store);
        owner.setFullName("Store Owner");
        owner.setEmail(email);
        owner.setPasswordHash(passwordEncoder.encode(request.password()));
        owner.setRole(Role.OWNER);
        owner.setStatus(UserStatus.ACTIVE);
        owner.setMustChangePassword(false);
        owner = userRepository.save(owner);

        store.setOwner(owner);
        storeRepository.save(store);

        return tokenResponse(owner);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AppException(ErrorCode.USER_DISABLED, HttpStatus.FORBIDDEN, "User is disabled");
        }
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Invitation has not been accepted");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return tokenResponse(user);
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
        return tokenResponse(userRepository.save(user));
    }

    private AuthResponse tokenResponse(AppUser user) {
        String accessToken = jwtUtil.createAccessToken(user);
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(user.getId(), refreshToken, Duration.ofDays(jwtProperties.refreshTokenDays()));
        Long storeId = user.getStore() == null ? null : user.getStore().getId();
        return new AuthResponse(accessToken, refreshToken, user.getId(), storeId, user.getRole(), user.isMustChangePassword());
    }
}
