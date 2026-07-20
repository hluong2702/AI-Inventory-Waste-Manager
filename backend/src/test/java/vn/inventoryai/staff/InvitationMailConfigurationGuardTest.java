package vn.inventoryai.staff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mock.env.MockEnvironment;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationMailConfigurationGuardTest {
    @Test
    void productionRequiresNonPlaceholderSmtpCredentials() {
        MailProperties mail = new MailProperties();
        mail.setUsername("");
        mail.setPassword("");

        assertThatThrownBy(() -> guard(true, mail, new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_USERNAME");

        mail.setUsername("your-gmail-address@gmail.com");
        mail.setPassword("your-16-character-gmail-app-password");
        assertThatThrownBy(() -> guard(true, mail, new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void productionCannotDisableInvitationDelivery() {
        assertThatThrownBy(() -> guard(false, new MailProperties(), new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be disabled");
    }

    @Test
    void disabledDevelopmentModeStartsButRejectsEmailDependentOperationsExplicitly() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        InvitationMailConfigurationGuard guard = guard(false, new MailProperties(), environment);

        guard.afterPropertiesSet();
        assertThatThrownBy(guard::assertDeliveryAvailable)
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
                    assertThat(exception.getStatus().value()).isEqualTo(503);
                });
    }

    @Test
    void validProductionCredentialsPassWithoutExposingTheSecret() {
        MailProperties mail = new MailProperties();
        mail.setUsername("mailer@inventory.example");
        mail.setPassword("a-strong-app-password-1234");

        InvitationMailConfigurationGuard guard = guard(true, mail, new MockEnvironment());
        guard.afterPropertiesSet();
        guard.assertDeliveryAvailable();
    }

    private InvitationMailConfigurationGuard guard(
            boolean enabled,
            MailProperties mail,
            MockEnvironment environment
    ) {
        return new InvitationMailConfigurationGuard(new InvitationMailProperties(enabled), mail, environment);
    }
}
