package vn.inventoryai.staff;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.staff.dto.AcceptInvitationRequest;
import vn.inventoryai.staff.dto.InvitationVerificationResponse;

@RestController
@RequestMapping("/api/staff/invitations")
@RequiredArgsConstructor
public class PublicInvitationController {
    private final StaffInvitationService staffInvitationService;

    @GetMapping("/verify")
    InvitationVerificationResponse verify(@RequestParam String token) {
        return staffInvitationService.verifyInvitation(token);
    }

    @PostMapping("/accept")
    InvitationVerificationResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return staffInvitationService.acceptInvitation(request);
    }
}
