package vn.inventoryai.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInventoryTransactionRequest(
        @NotNull TransactionType type,
        @NotNull TransactionReason reason,
        @Size(max = 40) String wasteReason,
        @NotEmpty @Size(max = 100) List<@Valid Item> items
) {
    public record Item(
            @NotNull @Positive Long ingredientId,
            @Size(max = 120) String batchNumber,
            @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @FutureOrPresent LocalDate expiredDate,
            @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 11, fraction = 3) BigDecimal costPerUnit
    ) {
    }

    public enum TransactionType { IMPORT, EXPORT }
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

    @AssertTrue(message = "waste reason is required for a waste export")
    public boolean isWasteReasonValid() {
        return reason != TransactionReason.EXPORT_WASTE || (wasteReason != null && !wasteReason.isBlank());
    }
}
