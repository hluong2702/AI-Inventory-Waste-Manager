package vn.inventoryai.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateInventoryTransactionRequest(
        @NotBlank String type,
        @NotBlank String reason,
        String wasteReason,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Long ingredientId,
            String batchNumber,
            @NotNull BigDecimal quantity,
            LocalDate expiredDate,
            BigDecimal costPerUnit
    ) {
    }
}
