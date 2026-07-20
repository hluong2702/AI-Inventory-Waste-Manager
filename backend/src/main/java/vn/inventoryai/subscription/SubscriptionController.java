package vn.inventoryai.subscription;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.common.security.ClientIpResolver;
import vn.inventoryai.subscription.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/plans")
    List<SubscriptionPlanResponse> plans() {
        return subscriptionService.plans();
    }

    @GetMapping("/current")
    CurrentSubscriptionResponse current() {
        return subscriptionService.current();
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasRole('OWNER')")
    UpgradeSubscriptionResponse upgrade(
            @Valid @RequestBody UpgradeSubscriptionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest
    ) {
        return subscriptionService.changePlan(request, clientIpResolver.resolve(httpRequest), idempotencyKey);
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasRole('OWNER')")
    CurrentSubscriptionResponse cancel(@RequestBody(required = false) CancelSubscriptionRequest request) {
        return subscriptionService.cancel(request == null ? new CancelSubscriptionRequest(true) : request);
    }
}
