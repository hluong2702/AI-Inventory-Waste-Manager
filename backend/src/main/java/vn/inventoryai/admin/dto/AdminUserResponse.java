package vn.inventoryai.admin.dto;

import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.UserStatus;

public record AdminUserResponse(
        Long id,
        Long storeId,
        String username,
        String email,
        String fullName,
        Role role,
        UserStatus status
) {
}
