package vn.inventoryai.integration.pos;

import org.springframework.web.multipart.MultipartFile;

public interface PosSalesImportService {
    String provider();

    PosSalesImportResult previewSales(MultipartFile file);
}
