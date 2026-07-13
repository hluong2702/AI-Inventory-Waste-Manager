package vn.inventoryai.staff;

public interface EmailService {
    void sendStaffInvitationEmail(String toEmail, String storeName, String invitationUrl);
}
