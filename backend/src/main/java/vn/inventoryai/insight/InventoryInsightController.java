package vn.inventoryai.insight;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.inventoryai.insight.dto.InventoryInsightResponse;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InventoryInsightController {
    private final InventoryInsightService inventoryInsightService;

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    List<InventoryInsightResponse> inventory() {
        return inventoryInsightService.inventoryInsights();
    }
}
