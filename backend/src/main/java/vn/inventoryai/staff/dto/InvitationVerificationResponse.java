package vn.inventoryai.staff.dto;

import vn.inventoryai.common.enums.Role;

public record InvitationVerificationResponse(
        InvitationStatus status,
        boolean valid,
        String email,
        String storeName,
        Role role,
        boolean accountSetupRequired
) {
    public static InvitationVerificationResponse invalid(InvitationStatus status) {
        return new InvitationVerificationResponse(status, false, null, null, null, false);
    }
}
