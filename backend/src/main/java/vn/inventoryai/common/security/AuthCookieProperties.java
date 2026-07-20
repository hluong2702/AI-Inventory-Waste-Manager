package vn.inventoryai.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record AuthCookieProperties(
        String name,
        boolean secure,
        String sameSite,
        String domain
) {
    private static final Set<String> ALLOWED_SAME_SITE = Set.of("Lax", "Strict", "None");

    public AuthCookieProperties {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("app.auth.refresh-cookie.name is invalid");
        }
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException("app.auth.refresh-cookie.same-site is required");
        }
        String normalized = sameSite.substring(0, 1).toUpperCase(Locale.ROOT)
                + sameSite.substring(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SAME_SITE.contains(normalized)) {
            throw new IllegalArgumentException("app.auth.refresh-cookie.same-site must be Lax, Strict or None");
        }
        if (normalized.equals("None") && !secure) {
            throw new IllegalArgumentException("SameSite=None refresh cookies must be Secure");
        }
        sameSite = normalized;
        domain = domain == null ? "" : domain.trim();
    }
}
