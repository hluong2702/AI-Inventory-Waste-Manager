package vn.inventoryai.integration.pos;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/integrations/pos")
@RequiredArgsConstructor
public class PosIntegrationController {
    private final List<PosSalesImportService> importServices;
    private final PosDeductionService posDeductionService;

    @PostMapping("/csv/preview")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    PosSalesImportResult previewCsv(@RequestParam("file") MultipartFile file) {
        return importServices.stream()
                .filter(service -> "CSV".equals(service.provider()))
                .findFirst()
                .orElseThrow()
                .previewSales(file);
    }

    @PostMapping("/csv/deduct")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    PosSalesImportResult deductCsv(@RequestParam("file") MultipartFile file) {
        return posDeductionService.deductSales(file);
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    List<ProviderCapability> providers() {
        return List.of(
                new ProviderCapability("CSV", "ACTIVE", true, "Nhập file CSV doanh số bán hàng để tự động trừ tồn kho theo công thức."),
                new ProviderCapability("KIOTVIET", "NOT_AVAILABLE", false, "Chưa có kết nối dữ liệu được cấu hình."),
                new ProviderCapability("SAPO", "NOT_AVAILABLE", false, "Chưa có kết nối dữ liệu được cấu hình."),
                new ProviderCapability("CUKCUK", "NOT_AVAILABLE", false, "Chưa có kết nối dữ liệu được cấu hình.")
        );
    }

    record ProviderCapability(String provider, String status, boolean persistsSales, String description) {
    }
}
