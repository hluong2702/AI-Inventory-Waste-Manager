package vn.inventoryai.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryBatchResponse(
        Long id,
        Long storeId,
        Long ingredientId,
        String batchNumber,
        BigDecimal quantity,
        LocalDate expiredDate,
        Instant importDate,
        BigDecimal costPerUnit
) {
}
