package vn.inventoryai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import vn.inventoryai.auth.AppUser;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenMinutes() * 60)))
                .signWith(key)
                .compact();
    }

    public JwtIdentity parseIdentity(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        String jwtId = claims.getId();
        if (jwtId == null || jwtId.isBlank()) {
            throw new IllegalArgumentException("JWT ID is required");
        }
        Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (remaining.isNegative() || remaining.isZero()) {
            throw new IllegalArgumentException("JWT is expired");
        }
        return new JwtIdentity(userId, jwtId, remaining);
    }

    public record JwtIdentity(Long userId, String jwtId, Duration remainingLifetime) {
    }
}
