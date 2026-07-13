package vn.inventoryai.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "Full name is required")
        @Size(max = 160, message = "Full name must be at most 160 characters")
        String fullName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password
) {
}
