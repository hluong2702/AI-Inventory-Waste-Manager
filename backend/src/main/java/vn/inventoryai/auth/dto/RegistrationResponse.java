package vn.inventoryai.auth.dto;

public record RegistrationResponse(
        boolean verificationRequired,
        String email,
        long expiresInHours
) {
}
