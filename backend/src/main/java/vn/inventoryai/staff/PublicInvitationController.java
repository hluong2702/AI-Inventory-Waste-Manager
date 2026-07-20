package vn.inventoryai.staff;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.staff.dto.AcceptInvitationRequest;
import vn.inventoryai.staff.dto.InvitationTokenRequest;
import vn.inventoryai.staff.dto.InvitationVerificationResponse;

@RestController
@RequestMapping("/api/staff/invitations")
@RequiredArgsConstructor
public class PublicInvitationController {
    private final StaffInvitationService staffInvitationService;

    @PostMapping("/verify")
    InvitationVerificationResponse verify(@Valid @RequestBody InvitationTokenRequest request) {
        return staffInvitationService.verifyInvitation(request.token());
    }

    @PostMapping("/accept")
    InvitationVerificationResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return staffInvitationService.acceptInvitation(request);
    }
}
