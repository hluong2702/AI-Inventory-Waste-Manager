package vn.inventoryai.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.admin.dto.*;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/dashboard")
    AdminDashboardResponse dashboard() {
        return adminService.dashboard();
    }

    @GetMapping("/stats")
    AdminStatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    List<AdminUserResponse> users() {
        return adminService.users();
    }

    @GetMapping("/stores")
    List<AdminStoreResponse> stores(
            @RequestParam(required = false) SubscriptionPlan plan,
            @RequestParam(required = false) StoreStatus status
    ) {
        return adminService.stores(plan, status);
    }

    @PatchMapping("/stores/{id}/status")
    AdminStoreResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStoreStatusRequest request) {
        return adminService.updateStatus(id, request);
    }
}
