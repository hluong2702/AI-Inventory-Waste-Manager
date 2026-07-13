package vn.inventoryai.report.dto;

import vn.inventoryai.common.enums.StockTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StockTransactionResponse(
        Long id,
        Long storeId,
        String type,
        String reason,
        Instant createdAt,
        String recordedBy,
        List<Item> items
) {
    public record Item(Long ingredientId, String batchNumber, Long batchId, BigDecimal quantity, LocalDate expiredDate, BigDecimal costPerUnit) {
    }

    public static String legacyType(StockTransactionType type) {
        return type == StockTransactionType.IN ? "IMPORT" : "EXPORT";
    }
}
