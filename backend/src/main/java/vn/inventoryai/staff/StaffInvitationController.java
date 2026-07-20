package vn.inventoryai.staff;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.staff.dto.InviteStaffRequest;
import vn.inventoryai.staff.dto.StaffResponse;

import java.util.List;

@RestController
@RequestMapping("/api/stores/{storeId}/staff")
@RequiredArgsConstructor
public class StaffInvitationController {
    private final StaffInvitationService staffInvitationService;
    private final InvitationMailConfigurationGuard invitationMailConfiguration;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER') and @storeAccess.canAccessStore(#storeId)")
    List<StaffResponse> list(@PathVariable Long storeId) {
        return staffInvitationService.listStaff(storeId);
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER') and @storeAccess.canAccessStore(#storeId)")
    List<StaffResponse> invite(@PathVariable Long storeId, @Valid @RequestBody InviteStaffRequest request) {
        invitationMailConfiguration.assertDeliveryAvailable();
        return staffInvitationService.invite(storeId, request);
    }

    @DeleteMapping("/invitations/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER') and @storeAccess.canAccessStore(#storeId)")
    List<StaffResponse> revokeInvitation(@PathVariable Long storeId, @PathVariable Long userId) {
        return staffInvitationService.revokeInvitation(storeId, userId);
    }

    @PatchMapping("/{userId}/disable")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER') and @storeAccess.canAccessStore(#storeId)")
    List<StaffResponse> disableStaff(@PathVariable Long storeId, @PathVariable Long userId) {
        return staffInvitationService.disableStaff(storeId, userId);
    }

    @PatchMapping("/{userId}/enable")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER') and @storeAccess.canAccessStore(#storeId)")
    List<StaffResponse> enableStaff(@PathVariable Long storeId, @PathVariable Long userId) {
        return staffInvitationService.enableStaff(storeId, userId);
    }
}
