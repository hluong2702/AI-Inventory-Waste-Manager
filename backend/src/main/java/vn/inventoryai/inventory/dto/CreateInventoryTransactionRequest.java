package vn.inventoryai.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInventoryTransactionRequest(
        @NotNull TransactionType type,
        @NotNull TransactionReason reason,
        String wasteReason,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Long ingredientId,
            String batchNumber,
            @NotNull @Positive BigDecimal quantity,
            LocalDate expiredDate,
            BigDecimal costPerUnit
    ) {
    }

    public enum TransactionType { IMPORT, EXPORT, ADJUSTMENT }
    public enum TransactionReason { IMPORT_NEW, EXPORT_CONSUME, EXPORT_WASTE, EXPORT_ADJUST }

    @AssertTrue(message = "expiry date is required for every import item")
    public boolean isExpiryDateValid() {
        return type != TransactionType.IMPORT || items == null || items.stream().allMatch(item -> item.expiredDate() != null);
    }

    @AssertTrue(message = "reason is not valid for the selected transaction type")
    public boolean isReasonValid() {
        if (type == null || reason == null) return true;
        return type == TransactionType.IMPORT ? reason == TransactionReason.IMPORT_NEW : reason != TransactionReason.IMPORT_NEW;
    }
}
