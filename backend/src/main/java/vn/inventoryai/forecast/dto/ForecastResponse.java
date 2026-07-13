package vn.inventoryai.forecast.dto;

import java.math.BigDecimal;

public record ForecastResponse(
        Long storeId,
        Long ingredientId,
        String ingredientName,
        String ingredientCode,
        String unit,
        BigDecimal avgDailyUsage,
        BigDecimal currentStock,
        BigDecimal minStock,
        BigDecimal recommendedOrder
) {
}
