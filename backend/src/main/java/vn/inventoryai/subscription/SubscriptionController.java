package vn.inventoryai.subscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.subscription.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    List<SubscriptionPlanResponse> plans() {
        return subscriptionService.plans();
    }

    @GetMapping("/current")
    CurrentSubscriptionResponse current() {
        return subscriptionService.current();
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    UpgradeSubscriptionResponse upgrade(@Valid @RequestBody UpgradeSubscriptionRequest request) {
        return subscriptionService.changePlan(request);
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    CurrentSubscriptionResponse cancel(@RequestBody(required = false) CancelSubscriptionRequest request) {
        return subscriptionService.cancel(request == null ? new CancelSubscriptionRequest(true) : request);
    }
}
