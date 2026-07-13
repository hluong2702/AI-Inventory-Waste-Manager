package vn.inventoryai.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Instant createdAt,
        Long storeId,
        String storeName,
        Long ingredientId,
        String ingredientName,
        String batchNumber,
        String action,
        String reason,
        BigDecimal quantity,
        String unit,
        String actorEmail
) {
}
