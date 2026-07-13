package vn.inventoryai.insight.dto;

import vn.inventoryai.insight.WasteRiskLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventoryInsightResponse(
        Long storeId,
        Long ingredientId,
        String ingredientName,
        String ingredientCode,
        String unit,
        Long nearestBatchId,
        LocalDate nearestBatchExpiryDate,
        BigDecimal avgDailyUsage7d,
        BigDecimal avgDailyUsage28d,
        BigDecimal weekdayAdjustedUsage,
        BigDecimal currentStock,
        BigDecimal daysUntilStockout,
        Integer daysUntilExpiry,
        WasteRiskLevel wasteRiskLevel,
        BigDecimal recommendedOrderQty,
        List<String> explanationBullets,
        String ctaLabel
) {
}
