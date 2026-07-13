package vn.inventoryai.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryInRequest(
        @NotNull Long ingredientId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @FutureOrPresent LocalDate expiryDate
) {
}
