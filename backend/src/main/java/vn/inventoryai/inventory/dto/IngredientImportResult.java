package vn.inventoryai.inventory.dto;

import java.util.List;

public record IngredientImportResult(
        int imported,
        int skipped,
        List<String> errors
) {
}
