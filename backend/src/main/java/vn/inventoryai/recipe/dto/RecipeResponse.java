package vn.inventoryai.recipe.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecipeResponse(
        Long id,
        Long storeId,
        String code,
        String name,
        BigDecimal price,
        boolean active,
        List<IngredientItemResponse> ingredients,
        Instant createdAt
) {
    public record IngredientItemResponse(
            Long ingredientId,
            String ingredientCode,
            String ingredientName,
            String unit,
            BigDecimal quantity
    ) {
    }
}
