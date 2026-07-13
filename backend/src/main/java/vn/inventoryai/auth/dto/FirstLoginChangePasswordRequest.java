package vn.inventoryai.auth.dto;

import jakarta.validation.constraints.Size;

public record FirstLoginChangePasswordRequest(
        @Size(min = 8, message = "new password must be at least 8 characters") String newPassword
) {
}
