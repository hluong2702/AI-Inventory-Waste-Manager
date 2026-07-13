package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.AppUser;
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
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.store.Subscription;
import vn.inventoryai.store.SubscriptionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffInvitationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StoreAccessService storeAccessService;
    private final StoreRepository storeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PlanEntitlementService planEntitlementService;
    private final StaffInviteRateLimiter staffInviteRateLimiter;

    @Value("${app.frontend.invite-url}")
    private String inviteUrl;

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Long storeId) {
        storeAccessService.assertCurrentStore(storeId);
        return userRepository.findByStoreIdAndRoleIn(storeId, List.of(Role.MANAGER, Role.STAFF))
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
        staffInviteRateLimiter.check(principal.userId(), storeId);

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT, "Email này đã tồn tại hoặc đã được mời.");
        }

        Subscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        Integer maxStaff = planEntitlementService.limits(subscription.getPlan()).staff();
        if (invitedRole == Role.STAFF && maxStaff != null) {
            long currentStaff = userRepository.countByStoreIdAndRoleAndStatusNot(storeId, Role.STAFF, UserStatus.DISABLED);
            if (currentStaff >= maxStaff) {
                throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Staff limit exceeded for current plan");
            }
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        String rawToken = randomToken();

        AppUser user = new AppUser();
        user.setStore(store);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(randomToken()));
        user.setRole(invitedRole);
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setMustChangePassword(false);
        user = userRepository.save(user);

        InviteToken inviteToken = new InviteToken();
        inviteToken.setUser(user);
        inviteToken.setTokenHash(hashToken(rawToken));
        inviteToken.setExpiresAt(Instant.now().plusSeconds(48 * 60 * 60));
        inviteToken.setUsed(false);
        inviteTokenRepository.save(inviteToken);

        emailService.sendStaffInvitationEmail(email, store.getName(), inviteUrl + "?token=" + rawToken);
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
        InviteToken inviteToken = inviteTokenRepository.findByTokenHash(hashToken(request.token()))
                .orElseThrow(() -> invalidToken("Invitation token is invalid"));

        InvitationVerificationResponse verification = verificationResponse(inviteToken);
        if (!verification.valid()) {
            throw invalidToken(messageFor(verification.status()));
        }

        AppUser user = inviteToken.getUser();
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        inviteToken.setUsed(true);
        inviteTokenRepository.save(inviteToken);
        userRepository.save(user);

        return new InvitationVerificationResponse(InvitationStatus.VALID, true, user.getEmail(), user.getStore().getName(), user.getRole());
    }

    @Transactional
    public List<StaffResponse> revokeInvitation(Long storeId, Long userId) {
        storeAccessService.assertCurrentStore(storeId);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStore() == null || !user.getStore().getId().equals(storeId)) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "User does not belong to current store");
        }
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only pending invitation can be revoked");
        }
        inviteTokenRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
        return listStaff(storeId);
    }

    @Transactional
    public List<StaffResponse> disableStaff(Long storeId, Long userId) {
        storeAccessService.assertCurrentStore(storeId);
        UserPrincipal principal = SecurityUtils.principal();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStore() == null || !user.getStore().getId().equals(storeId)) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "User does not belong to current store");
        }
        if (user.getRole() == Role.MANAGER && principal.role() != Role.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only owner can disable manager");
        }
        if (user.getRole() == Role.OWNER || user.getRole() == Role.SYSTEM_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Cannot disable this role from staff API");
        }
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        return listStaff(storeId);
    }

    @Transactional
    public List<StaffResponse> enableStaff(Long storeId, Long userId) {
        storeAccessService.assertCurrentStore(storeId);
        UserPrincipal principal = SecurityUtils.principal();
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStore() == null || !user.getStore().getId().equals(storeId)) {
            throw new AppException(ErrorCode.STORE_MISMATCH, HttpStatus.FORBIDDEN, "User does not belong to current store");
        }
        if (user.getRole() == Role.MANAGER && principal.role() != Role.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only owner can enable manager");
        }
        if (user.getRole() == Role.OWNER || user.getRole() == Role.SYSTEM_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Cannot enable this role from staff API");
        }
        if (user.getStatus() != UserStatus.DISABLED) {
            throw new AppException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only disabled user can be enabled");
        }
        if (user.getRole() == Role.STAFF) {
            Subscription subscription = subscriptionRepository.findByStoreId(storeId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
            Integer maxStaff = planEntitlementService.limits(subscription.getPlan()).staff();
            if (maxStaff != null) {
                long currentStaff = userRepository.countByStoreIdAndRoleAndStatusNot(storeId, Role.STAFF, UserStatus.DISABLED);
                if (currentStaff >= maxStaff) {
                    throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Staff limit exceeded for current plan");
                }
            }
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
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
        return new InvitationVerificationResponse(InvitationStatus.VALID, true, user.getEmail(), user.getStore().getName(), user.getRole());
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

    private StaffResponse toResponse(AppUser user) {
        return new StaffResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getStatus(), user.isMustChangePassword());
    }
}
