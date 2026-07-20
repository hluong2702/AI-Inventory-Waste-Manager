package vn.inventoryai.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.report.dto.AuditLogResponse;
import vn.inventoryai.report.dto.StockTransactionResponse;
import vn.inventoryai.report.dto.WasteDashboardResponse;
import vn.inventoryai.report.dto.WasteRecordResponse;
import vn.inventoryai.report.dto.WasteReportSummaryResponse;
import vn.inventoryai.subscription.feature.RequiresFeature;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/waste")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<WasteRecordResponse> waste(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable
    ) {
        return reportService.waste(SecurityUtils.storeId(), startDate, endDate, pageable);
    }

    @GetMapping("/waste/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    WasteReportSummaryResponse wasteSummary(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return reportService.wasteSummary(SecurityUtils.storeId(), startDate, endDate);
    }

    @GetMapping("/waste/export")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @RequiresFeature("EXPORT_REPORTS")
    ResponseEntity<StreamingResponseBody> exportWaste(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return reportService.exportWaste(SecurityUtils.storeId(), startDate, endDate);
    }

    @GetMapping("/inventory/export")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @RequiresFeature("EXPORT_REPORTS")
    ResponseEntity<StreamingResponseBody> exportInventory(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return reportService.exportInventory(SecurityUtils.storeId(), startDate, endDate);
    }

    @GetMapping("/waste/dashboard")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    WasteDashboardResponse wasteDashboard(@RequestParam(defaultValue = "month") String period) {
        return reportService.wasteDashboard(SecurityUtils.storeId(), period);
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<AuditLogResponse> auditLog(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable
    ) {
        return reportService.auditLog(SecurityUtils.storeId(), startDate, endDate, pageable);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    Page<StockTransactionResponse> transactions(Pageable pageable) {
        return reportService.transactions(SecurityUtils.storeId(), pageable);
    }
}
