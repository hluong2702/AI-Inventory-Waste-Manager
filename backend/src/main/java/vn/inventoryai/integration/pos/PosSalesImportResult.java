package vn.inventoryai.integration.pos;

import java.math.BigDecimal;
import java.util.List;

public record PosSalesImportResult(
        String provider,
        int rowsImported,
        BigDecimal totalRevenue,
        List<String> warnings
) {
}
