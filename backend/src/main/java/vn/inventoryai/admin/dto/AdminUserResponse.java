package vn.inventoryai.admin.dto;

import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.UserStatus;

import java.util.List;

public record AdminUserResponse(
        Long id,
        Long storeId,
        String username,
        String email,
        String fullName,
        Role role,
        UserStatus status,
        List<Membership> memberships
) {
    public record Membership(
            Long storeId,
            String storeName,
            Role role,
            UserStatus status
    ) {
    }
}
