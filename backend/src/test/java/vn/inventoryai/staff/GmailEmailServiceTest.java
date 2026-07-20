package vn.inventoryai.staff;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GmailEmailServiceTest {
    @Test
    void stripsControlCharactersFromSubjectAndEscapesHtml() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        GmailEmailService service = new GmailEmailService(mailSender);

        service.sendStaffInvitationEmail(
                "staff@coffee.vn",
                "Coffee\r\nBcc: attacker@example.com <script>",
                "https://app.test/accept?token=a&next=\"bad\""
        );

        assertThat(message.getSubject()).doesNotContain("\r", "\n");
        assertThat(message.getSubject()).contains("Coffee Bcc: attacker@example.com");
        assertThat(message.getContent().toString()).contains("&lt;script&gt;").doesNotContain("<script>");
        verify(mailSender).send(message);
    }

    @Test
    void propagatesDeliveryFailureForOutboxRetry() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(message);

        assertThatThrownBy(() -> new GmailEmailService(mailSender)
                .sendStaffInvitationEmail("staff@coffee.vn", "Coffee", "https://app.test/accept"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Invitation email delivery failed");
    }
}
