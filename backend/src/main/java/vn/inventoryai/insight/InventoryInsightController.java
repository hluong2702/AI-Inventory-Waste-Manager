package vn.inventoryai.insight;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.inventoryai.subscription.feature.RequiresFeature;
import vn.inventoryai.insight.dto.InventoryInsightResponse;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Validated
public class InventoryInsightController {
    private final InventoryInsightService inventoryInsightService;

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    @RequiresFeature("ADVANCED_FORECAST")
    Page<InventoryInsightResponse> inventory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return inventoryInsightService.inventoryInsights(PageRequest.of(page, size));
    }
}
