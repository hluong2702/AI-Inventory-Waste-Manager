package vn.inventoryai.staff;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailEmailService implements EmailService {
    private final JavaMailSender mailSender;

    @Async
    @Override
    public void sendStaffInvitationEmail(String toEmail, String storeName, String invitationUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Lời mời tham gia " + storeName + " trên AI Inventory");
            helper.setText(buildInvitationHtml(storeName, invitationUrl), true);

            mailSender.send(message);
            log.info("Sent staff invitation email to {}", toEmail);
        } catch (Exception ex) {
            log.error("Failed to send staff invitation email to {}: {}", toEmail, ex.getMessage(), ex);
        }
    }

    private String buildInvitationHtml(String storeName, String invitationUrl) {
        String safeStoreName = HtmlUtils.htmlEscape(storeName);
        String safeInvitationUrl = HtmlUtils.htmlEscape(invitationUrl);

        return """
                <!doctype html>
                <html lang="vi">
                <body style="margin:0;background:#f6f4ee;font-family:Arial,Helvetica,sans-serif;color:#1f2722;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f6f4ee;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e4dfd2;">
                          <tr>
                            <td style="background:#2f5f4a;padding:28px 32px;color:#ffffff;">
                              <div style="font-size:13px;font-weight:700;letter-spacing:.04em;text-transform:uppercase;opacity:.82;">AI Inventory & Waste Manager</div>
                              <h1 style="margin:10px 0 0;font-size:24px;line-height:1.25;">Bạn được mời tham gia quản lý kho</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:30px 32px;">
                              <p style="margin:0 0 14px;font-size:15px;line-height:1.7;">Xin chào,</p>
                              <p style="margin:0 0 18px;font-size:15px;line-height:1.7;">
                                Quán <strong>{{STORE_NAME}}</strong> đã mời bạn tham gia hệ thống quản lý tồn kho và thất thoát nguyên liệu.
                              </p>
                              <p style="margin:0 0 22px;font-size:14px;line-height:1.7;color:#5c665f;">
                                Liên kết này có hiệu lực trong 48 giờ. Vui lòng mở liên kết để đặt tên hiển thị và mật khẩu của bạn.
                              </p>
                              <a href="{{INVITATION_URL}}" style="display:inline-block;background:#2f5f4a;color:#ffffff;text-decoration:none;font-weight:700;font-size:14px;padding:13px 18px;border-radius:12px;">
                                Chấp nhận lời mời
                              </a>
                              <p style="margin:24px 0 0;font-size:12px;line-height:1.6;color:#777f78;">
                                Nếu nút không hoạt động, hãy mở liên kết này trong trình duyệt:<br>
                                <a href="{{INVITATION_URL}}" style="color:#2f5f4a;">{{INVITATION_URL}}</a>
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{STORE_NAME}}", safeStoreName)
                .replace("{{INVITATION_URL}}", safeInvitationUrl);
    }
}
