package vn.inventoryai.staff;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.store.Store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class OwnerActivationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final InviteTokenRepository inviteTokenRepository;
    private final InvitationEmailOutboxService emailOutboxService;

    @Value("${app.frontend.invite-url}")
    private String activationUrl;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(AppUser owner, Store store, TenantMembership membership) {
        String rawToken = randomToken();
        InviteToken inviteToken = new InviteToken();
        inviteToken.setUser(owner);
        inviteToken.setMembership(membership);
        inviteToken.setTokenHash(hashToken(rawToken));
        inviteToken.setExpiresAt(Instant.now().plusSeconds(48 * 60 * 60));
        inviteToken.setUsed(false);
        inviteToken = inviteTokenRepository.save(inviteToken);
        emailOutboxService.enqueue(
                inviteToken,
                owner.getEmail(),
                store.getName(),
                activationUrl + "#token=" + rawToken
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
