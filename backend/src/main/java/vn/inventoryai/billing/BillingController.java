package vn.inventoryai.billing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.inventoryai.billing.dto.BillingEntitlementsResponse;
import vn.inventoryai.billing.dto.ChangePlanRequest;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService billingService;

    @GetMapping("/entitlements")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    BillingEntitlementsResponse entitlements() {
        return billingService.entitlements();
    }

    @PatchMapping("/plan")
    @PreAuthorize("hasRole('OWNER')")
    BillingEntitlementsResponse changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return billingService.changePlan(request);
    }
}
