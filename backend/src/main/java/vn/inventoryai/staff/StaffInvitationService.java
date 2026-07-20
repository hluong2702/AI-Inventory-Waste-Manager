package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.common.security.StoreAccessService;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.staff.dto.InviteStaffRequest;
import vn.inventoryai.staff.dto.AcceptInvitationRequest;
import vn.inventoryai.staff.dto.InvitationStatus;
import vn.inventoryai.staff.dto.InvitationVerificationResponse;
import vn.inventoryai.staff.dto.StaffResponse;
import vn.inventoryai.store.Store;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StaffInvitationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<Role> SEAT_ROLES = List.of(Role.MANAGER, Role.STAFF);

    private final StoreAccessService storeAccessService;
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvitationEmailOutboxService emailOutboxService;
    private final PlanEntitlementService planEntitlementService;
    private final StaffInviteRateLimiter staffInviteRateLimiter;

    @Value("${app.frontend.invite-url}")
    private String inviteUrl;

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Long storeId) {
        storeAccessService.assertCurrentStore(storeId);
        return tenantMembershipRepository.findAllByStoreIdAndRoleInOrderByUserFullNameAsc(
                        storeId,
                        SEAT_ROLES
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<StaffResponse> invite(Long storeId, InviteStaffRequest request) {
        storeAccessService.assertCurrentStore(storeId);
        UserPrincipal principal = SecurityUtils.principal();
        Role invitedRole = request.role();

        if (invitedRole != Role.MANAGER && invitedRole != Role.STAFF) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Only MANAGER or STAFF can be invited");
        }
        if (principal.role() == Role.MANAGER && invitedRole == Role.MANAGER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Manager cannot invite another manager");
        }

        TenantSubscription subscription = lockSubscriptionAndAssertSeatAvailable(storeId);
        String email = normalizeEmail(request.email());
        staffInviteRateLimiter.check(principal.userId(), storeId, email);

        Store store = subscription.getTenant();
        String rawToken = randomToken();

        AppUser user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.getStatus() == UserStatus.DISABLED) {
            throw new AppException(
                    ErrorCode.USER_DISABLED,
                    HttpStatus.CONFLICT,
                    "Tài khoản này đã bị vô hiệu hóa ở cấp hệ thống"
            );
        }
        if (user != null && tenantMembershipRepository.findByUserIdAndStoreId(user.getId(), storeId).isPresent()) {
            throw new AppException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    HttpStatus.CONFLICT,
                    "Email này đã là thành viên hoặc đang có lời mời tại cửa hàng"
            );
        }
        if (user == null) {
            user = createPendingIdentity(store, email, invitedRole);
        }

        TenantMembership membership = new TenantMembership();
        membership.setStore(store);
        membership.setUser(user);
        membership.setRole(invitedRole);
        membership.setStatus(UserStatus.PENDING_ACTIVATION);
        try {
            membership = tenantMembershipRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    HttpStatus.CONFLICT,
                    "Email này đã là thành viên hoặc đang có lời mời tại cửa hàng"
            );
        }

        InviteToken inviteToken = new InviteToken();
        inviteToken.setUser(user);
        inviteToken.setMembership(membership);
        inviteToken.setTokenHash(hashToken(rawToken));
        inviteToken.setExpiresAt(Instant.now().plusSeconds(48 * 60 * 60));
        inviteToken.setUsed(false);
        inviteToken = inviteTokenRepository.save(inviteToken);

        emailOutboxService.enqueue(inviteToken, email, store.getName(), inviteUrl + "#token=" + rawToken);
        return listStaff(storeId);
    }

    @Transactional(readOnly = true)
    public InvitationVerificationResponse verifyInvitation(String token) {
        return inviteTokenRepository.findByTokenHash(hashToken(token))
                .map(this::verificationResponse)
                .orElseGet(() -> InvitationVerificationResponse.invalid(InvitationStatus.INVALID));
    }

    @Transactional
    public InvitationVerificationResponse acceptInvitation(AcceptInvitationRequest request) {
        InviteToken inviteToken = inviteTokenRepository.findByTokenHashForUpdate(hashToken(request.token()))
                .orElseThrow(() -> invalidToken("Invitation token is invalid"));

        InvitationVerificationResponse verification = verificationResponse(inviteToken);
        if (!verification.valid()) {
            throw invalidToken(messageFor(verification.status()));
        }

        AppUser user = inviteToken.getUser();
        TenantMembership membership = inviteToken.getMembership();
        if (user.getStatus() == UserStatus.DISABLED
                || membership.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw invalidToken("Invitation token is invalid");
        }
        boolean accountSetupRequired = user.getStatus() == UserStatus.PENDING_ACTIVATION;
        if (accountSetupRequired) {
            assertAccountSetupInput(request);
            user.setFullName(request.fullName().trim());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setStatus(UserStatus.ACTIVE);
            user.setMustChangePassword(false);
            userRepository.save(user);
        }
        membership.setStatus(UserStatus.ACTIVE);
        membership.setUpdatedAt(Instant.now());
        inviteToken.setUsed(true);
        inviteTokenRepository.save(inviteToken);
        tenantMembershipRepository.save(membership);

        return validResponse(inviteToken, accountSetupRequired);
    }

    @Transactional
    public List<StaffResponse> revokeInvitation(Long storeId, Long userId) {
        storeAccessService.assertCurrentStore(storeId);
        UserPrincipal principal = SecurityUtils.principal();
        TenantMembership membership = lockedMembership(userId, storeId);
        assertManageableTarget(principal, membership);
        if (membership.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only pending invitation can be revoked");
        }
        AppUser user = membership.getUser();
        inviteTokenRepository.deleteByMembershipId(membership.getId());
        tenantMembershipRepository.delete(membership);
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION
                && tenantMembershipRepository.countByUserId(user.getId()) == 0) {
            userRepository.delete(user);
        }
        return listStaff(storeId);
    }

    @Transactional
    public List<StaffResponse> disableStaff(Long storeId, Long userId) {
        storeAccessService.assertCurrentStore(storeId);
        UserPrincipal principal = SecurityUtils.principal();
        TenantMembership membership = lockedMembership(userId, storeId);
        assertManageableTarget(principal, membership);
        if (membership.getStatus() == UserStatus.PENDING_ACTIVATION) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.CONFLICT,
                    "Hãy thu hồi lời mời đang chờ thay vì vô hiệu hóa thành viên"
            );
        }
        membership.setStatus(UserStatus.DISABLED);
        membership.setUpdatedAt(Instant.now());
        tenantMembershipRepository.save(membership);
        return listStaff(storeId);
    }

    @Transactional
    public List<StaffResponse> enableStaff(Long storeId, Long userId) {
        storeAccessService.assertCurrentStore(storeId);
        UserPrincipal principal = SecurityUtils.principal();
        TenantMembership membership = lockedMembership(userId, storeId);
        assertManageableTarget(principal, membership);
        if (membership.getStatus() != UserStatus.DISABLED) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only disabled user can be enabled");
        }
        if (membership.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_DISABLED, HttpStatus.CONFLICT,
                    "Tài khoản chưa được kích hoạt hoặc đã bị vô hiệu hóa ở cấp hệ thống");
        }
        lockSubscriptionAndAssertSeatAvailable(storeId);

        membership.setStatus(UserStatus.ACTIVE);
        membership.setUpdatedAt(Instant.now());
        tenantMembershipRepository.save(membership);
        return listStaff(storeId);
    }

    private InvitationVerificationResponse verificationResponse(InviteToken inviteToken) {
        if (inviteToken.isUsed()) {
            return InvitationVerificationResponse.invalid(InvitationStatus.USED);
        }
        if (inviteToken.getExpiresAt().isBefore(Instant.now())) {
            return InvitationVerificationResponse.invalid(InvitationStatus.EXPIRED);
        }
        AppUser user = inviteToken.getUser();
        TenantMembership membership = inviteToken.getMembership();
        if (user.getStatus() == UserStatus.DISABLED
                || membership.getStatus() != UserStatus.PENDING_ACTIVATION) {
            return InvitationVerificationResponse.invalid(InvitationStatus.INVALID);
        }
        return validResponse(inviteToken, user.getStatus() == UserStatus.PENDING_ACTIVATION);
    }

    private TenantSubscription lockSubscriptionAndAssertSeatAvailable(Long storeId) {
        TenantSubscription subscription = tenantSubscriptionRepository.findActiveForUpdate(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        Integer maxSeats = planEntitlementService.limits(subscription.getPlan().getCode()).staff();
        if (maxSeats == null) {
            return subscription;
        }

        long occupiedSeats = tenantMembershipRepository.countByStoreIdAndRoleInAndStatusNot(
                storeId,
                SEAT_ROLES,
                UserStatus.DISABLED
        );
        if (occupiedSeats >= maxSeats) {
            throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT,
                    "Staff seat limit exceeded for current plan");
        }
        return subscription;
    }

    private void assertManageableTarget(UserPrincipal principal, TenantMembership membership) {
        if (membership.getRole() == Role.OWNER || membership.getRole() == Role.SYSTEM_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Cannot manage this role from staff API");
        }
        if (membership.getRole() == Role.MANAGER && principal.role() != Role.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Only owner can manage a manager");
        }
    }

    private TenantMembership lockedMembership(Long userId, Long storeId) {
        return tenantMembershipRepository.findByUserIdAndStoreIdForUpdate(userId, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Tenant membership not found"));
    }

    private AppUser createPendingIdentity(Store store, String email, Role invitedRole) {
        AppUser user = new AppUser();
        user.setStore(store);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(randomToken()));
        user.setRole(invitedRole);
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setMustChangePassword(false);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    HttpStatus.CONFLICT,
                    "Email vừa được tạo ở một cửa hàng khác; vui lòng thử gửi lời mời lại"
            );
        }
    }

    private void assertAccountSetupInput(AcceptInvitationRequest request) {
        String fullName = request.fullName();
        String password = request.password();
        if (fullName == null || fullName.isBlank() || fullName.trim().length() < 2
                || password == null || password.length() < 8 || password.length() > 128) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Họ tên và mật khẩu từ 8 đến 128 ký tự là bắt buộc khi tạo tài khoản mới"
            );
        }
    }

    private InvitationVerificationResponse validResponse(InviteToken inviteToken, boolean accountSetupRequired) {
        TenantMembership membership = inviteToken.getMembership();
        return new InvitationVerificationResponse(
                InvitationStatus.VALID,
                true,
                inviteToken.getUser().getEmail(),
                membership.getStore().getName(),
                membership.getRole(),
                accountSetupRequired
        );
    }

    private AppException invalidToken(String message) {
        return new AppException(ErrorCode.TOKEN_INVALID, HttpStatus.BAD_REQUEST, message);
    }

    private String messageFor(InvitationStatus status) {
        return switch (status) {
            case EXPIRED -> "Invitation token has expired";
            case USED -> "Invitation token has already been used";
            case INVALID, VALID -> "Invitation token is invalid";
        };
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Email is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1 || normalized.length() > 180) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Email is invalid");
        }
        return normalized;
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken("Invitation token is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private StaffResponse toResponse(TenantMembership membership) {
        AppUser user = membership.getUser();
        return new StaffResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                membership.getRole(),
                membership.getStatus(),
                user.isMustChangePassword()
        );
    }
}
