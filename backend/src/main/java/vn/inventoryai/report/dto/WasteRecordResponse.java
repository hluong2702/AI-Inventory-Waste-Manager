package vn.inventoryai.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WasteRecordResponse(
        Long id,
        Long storeId,
        Long ingredientId,
        String ingredientName,
        String ingredientUnit,
        Long batchId,
        BigDecimal quantity,
        String reason,
        BigDecimal estimatedCost,
        String recordedBy,
        Instant createdAt
) {
}
