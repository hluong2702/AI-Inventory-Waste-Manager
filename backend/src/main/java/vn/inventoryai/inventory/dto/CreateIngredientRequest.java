package vn.inventoryai.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateIngredientRequest(
        String code,
        @NotBlank String name,
        @NotBlank String unit,
        String category,
        @NotNull @DecimalMin("0") BigDecimal minStock,
        @DecimalMin("0") BigDecimal maxStock,
        @DecimalMin("0") BigDecimal unitCost
) {
}
