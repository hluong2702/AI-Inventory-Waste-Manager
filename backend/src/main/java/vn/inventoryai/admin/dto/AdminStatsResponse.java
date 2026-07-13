package vn.inventoryai.admin.dto;

import java.math.BigDecimal;

public record AdminStatsResponse(
        long totalStores,
        long totalUsers,
        long totalIngredients,
        long totalTransactions,
        BigDecimal totalWasteCost
) {
}
