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

    @PostMapping("/csv/import")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    PosSalesImportResult importCsv(@RequestParam("file") MultipartFile file) {
        return importServices.stream()
                .filter(service -> "CSV".equals(service.provider()))
                .findFirst()
                .orElseThrow()
                .importSales(file);
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    List<String> providers() {
        return List.of("CSV", "KIOTVIET_READY", "SAPO_READY", "CUKCUK_READY");
    }
}
