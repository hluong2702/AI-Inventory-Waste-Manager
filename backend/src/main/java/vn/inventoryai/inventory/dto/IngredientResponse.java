package vn.inventoryai.inventory.dto;

import java.math.BigDecimal;

public record IngredientResponse(
        Long id,
        Long storeId,
        String code,
        String name,
        String unit,
        String category,
        BigDecimal minStock,
        BigDecimal maxStock,
        BigDecimal unitCost,
        boolean active
) {
}
