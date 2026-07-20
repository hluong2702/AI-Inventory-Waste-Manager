package vn.inventoryai.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WasteReportSummaryResponse(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalWasteCost,
        BigDecimal totalQuantity,
        long recordCount,
        long affectedIngredientCount,
        List<ReasonBreakdown> reasonBreakdown
) {
    public record ReasonBreakdown(
            String reason,
            BigDecimal estimatedCost,
            BigDecimal quantity,
            long recordCount
    ) {
    }
}
