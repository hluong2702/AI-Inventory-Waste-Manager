package vn.inventoryai.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationTokenRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 256, message = "Token is too long")
        String token
) {
}
