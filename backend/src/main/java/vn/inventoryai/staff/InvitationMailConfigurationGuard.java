package vn.inventoryai.staff;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class InvitationMailConfigurationGuard implements InitializingBean {
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("dev", "test", "local");
    private static final Set<String> PLACEHOLDER_MARKERS = Set.of(
            "placeholder",
            "your-gmail",
            "your-16-character",
            "change-me",
            "changeme",
            "dev-mail-disabled"
    );

    private final InvitationMailProperties properties;
    private final MailProperties mailProperties;
    private final Environment environment;

    public InvitationMailConfigurationGuard(
            InvitationMailProperties properties,
            MailProperties mailProperties,
            Environment environment
    ) {
        this.properties = properties;
        this.mailProperties = mailProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.enabled()) {
            if (!isNonProductionProfile()) {
                throw new IllegalStateException(
                        "Invitation email delivery cannot be disabled outside dev, test or local profiles"
                );
            }
            return;
        }

        validateUsername(mailProperties.getUsername());
        validatePassword(mailProperties.getPassword());
    }

    public void assertDeliveryAvailable() {
        if (!properties.enabled()) {
            throw new AppException(
                    ErrorCode.EMAIL_DELIVERY_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Invitation email delivery is disabled in this environment"
            );
        }
    }

    private boolean isNonProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(NON_PRODUCTION_PROFILES::contains);
    }

    private void validateUsername(String username) {
        String value = requireText(username, "MAIL_USERNAME");
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1 || value.length() > 254) {
            throw new IllegalStateException("MAIL_USERNAME must be a valid SMTP account email address");
        }
        rejectPlaceholder(value, "MAIL_USERNAME");
    }

    private void validatePassword(String password) {
        String value = requireText(password, "MAIL_APP_PASSWORD");
        long nonWhitespaceLength = value.chars().filter(character -> !Character.isWhitespace(character)).count();
        if (nonWhitespaceLength < 12) {
            throw new IllegalStateException("MAIL_APP_PASSWORD must contain at least 12 non-whitespace characters");
        }
        rejectPlaceholder(value, "MAIL_APP_PASSWORD");
    }

    private String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when invitation email delivery is enabled");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalStateException(property + " contains invalid control characters");
            }
        }
        return value.trim();
    }

    private void rejectPlaceholder(String value, String property) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (PLACEHOLDER_MARKERS.stream().anyMatch(normalized::contains)
                || normalized.endsWith("@example.com")
                || normalized.endsWith("@example.invalid")) {
            throw new IllegalStateException(property + " must not use a placeholder value");
        }
    }
}
