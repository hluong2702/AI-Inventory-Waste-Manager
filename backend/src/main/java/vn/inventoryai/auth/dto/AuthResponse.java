package vn.inventoryai.auth.dto;

import vn.inventoryai.common.enums.Role;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        Long storeId,
        Role role,
        boolean mustChangePassword
) {
}
