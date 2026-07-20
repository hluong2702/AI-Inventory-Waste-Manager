package vn.inventoryai.integration.pos;

import java.math.BigDecimal;
import java.util.List;

public record PosSalesImportResult(
        String provider,
        String mode,
        int rowsParsed,
        BigDecimal totalRevenue,
        boolean persisted,
        List<String> warnings
) {
}
