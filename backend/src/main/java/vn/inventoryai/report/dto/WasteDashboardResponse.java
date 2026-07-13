package vn.inventoryai.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record WasteDashboardResponse(
        String period,
        BigDecimal currentWasteCost,
        BigDecimal previousWasteCost,
        BigDecimal changePercent,
        List<TopWasteIngredient> topWasteIngredients
) {
    public record TopWasteIngredient(
            Long ingredientId,
            String ingredientName,
            String unit,
            BigDecimal quantity,
            BigDecimal estimatedCost
    ) {
    }
}
