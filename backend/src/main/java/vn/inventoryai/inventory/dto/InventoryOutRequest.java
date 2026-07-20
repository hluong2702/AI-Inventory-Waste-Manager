package vn.inventoryai.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InventoryOutRequest(
        @NotNull @Positive Long ingredientId,
        @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity
) {
}
