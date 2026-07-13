package vn.inventoryai.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryOutRequest(
        @NotNull Long ingredientId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity
) {
}
