package vn.inventoryai.inventory.dto;

import vn.inventoryai.common.enums.StockTransactionType;

import java.math.BigDecimal;

public record InventoryTransactionResponse(
        Long transactionId,
        Long ingredientId,
        Long batchId,
        StockTransactionType type,
        BigDecimal quantity
) {
}
