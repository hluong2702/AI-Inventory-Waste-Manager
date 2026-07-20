package vn.inventoryai.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateRecipeRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @Digits(integer = 11, fraction = 3) BigDecimal price,
        boolean active,
        @NotEmpty @Valid List<IngredientItem> ingredients
) {
    public record IngredientItem(
            @NotNull @Positive Long ingredientId,
            @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity
    ) {
    }
}
