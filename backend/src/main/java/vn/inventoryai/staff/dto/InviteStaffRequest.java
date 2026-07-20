package vn.inventoryai.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.inventoryai.common.enums.Role;

public record InviteStaffRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,

        @NotNull Role role
) {
}
