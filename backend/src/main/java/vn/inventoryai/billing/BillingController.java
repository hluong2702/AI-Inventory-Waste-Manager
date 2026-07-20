package vn.inventoryai.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.inventoryai.billing.dto.BillingEntitlementsResponse;

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

}
