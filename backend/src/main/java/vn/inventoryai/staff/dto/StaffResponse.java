package vn.inventoryai.staff.dto;

import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.UserStatus;

public record StaffResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        UserStatus status,
        boolean mustChangePassword
) {
}
