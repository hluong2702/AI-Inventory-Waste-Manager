package vn.inventoryai.inventory.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateIngredientRequest(
        @Size(max = 80) String code,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 40) String unit,
        @Size(max = 120) String category,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minStock,
        @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal maxStock,
        @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal unitCost
) {
    @AssertTrue(message = "max stock must be zero or greater than or equal to min stock")
    public boolean isStockRangeValid() {
        if (minStock == null || maxStock == null || maxStock.signum() == 0) {
            return true;
        }
        return maxStock.compareTo(minStock) >= 0;
    }
}
