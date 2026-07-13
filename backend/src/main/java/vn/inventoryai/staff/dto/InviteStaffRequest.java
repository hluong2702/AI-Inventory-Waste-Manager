package vn.inventoryai.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import vn.inventoryai.common.enums.Role;

public record InviteStaffRequest(
        @Email String email,
        @NotNull Role role
) {
}
