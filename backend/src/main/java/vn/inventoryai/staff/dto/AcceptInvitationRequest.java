package vn.inventoryai.staff.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 256, message = "Token is too long")
        String token,

        @Size(max = 160, message = "Full name must be at most 160 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "Full name contains invalid control characters")
        String fullName,

        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password
) {
}
