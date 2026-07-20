package vn.inventoryai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(URI loginUrl, URI inviteUrl) {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    public FrontendProperties {
        loginUrl = validate(loginUrl, "app.frontend.login-url");
        inviteUrl = validate(inviteUrl, "app.frontend.invite-url");
    }

    private static URI validate(URI value, String property) {
        if (value == null || !value.isAbsolute() || value.getHost() == null || value.getHost().isBlank()) {
            throw new IllegalArgumentException(property + " must be an absolute HTTP(S) URL with a host");
        }
        if (value.getUserInfo() != null || value.getFragment() != null || value.getRawQuery() != null) {
            throw new IllegalArgumentException(property + " must not contain credentials, query parameters or fragments");
        }

        String scheme = value.getScheme().toLowerCase(Locale.ROOT);
        String host = value.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && LOOPBACK_HOSTS.contains(host))) {
            throw new IllegalArgumentException(property + " must use HTTPS outside local development");
        }
        return value;
    }
}
