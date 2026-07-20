package vn.inventoryai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstLoginChangePasswordRequest(
        @NotBlank
        @Size(min = 8, max = 128, message = "new password must contain between 8 and 128 characters")
        String newPassword
) {
}
