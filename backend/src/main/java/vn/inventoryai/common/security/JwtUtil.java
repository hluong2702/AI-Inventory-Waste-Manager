package vn.inventoryai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.common.enums.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

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
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("storeId", user.getStore() == null ? null : user.getStore().getId())
                .claim("role", user.getRole().name())
                .claim("mustChangePassword", user.isMustChangePassword())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenMinutes() * 60)))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Number userId = claims.get("userId", Number.class);
        Number storeId = claims.get("storeId", Number.class);
        String role = claims.get("role", String.class);
        Boolean mustChangePassword = claims.get("mustChangePassword", Boolean.class);

        return new UserPrincipal(
                userId.longValue(),
                storeId == null ? null : storeId.longValue(),
                claims.getSubject(),
                Role.valueOf(role),
                Boolean.TRUE.equals(mustChangePassword)
        );
    }
}
