package vn.inventoryai.inventory.dto;

import java.math.BigDecimal;

public record InventorySummaryResponse(
        Long ingredientId,
        String code,
        String name,
        String unit,
        String category,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal totalQuantity,
        BigDecimal sellableQuantity,
        long activeBatchesCount,
        long expiredBatchesCount,
        long expiringSoonBatchesCount
) {
}
