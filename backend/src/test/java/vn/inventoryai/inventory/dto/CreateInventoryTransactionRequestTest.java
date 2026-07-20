package vn.inventoryai.inventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateInventoryTransactionRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositiveQuantityAndImportWithoutExpiryDate() {
        var item = new CreateInventoryTransactionRequest.Item(1L, "LOT-1", BigDecimal.ZERO, null, BigDecimal.ONE);
        var request = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.IMPORT,
                CreateInventoryTransactionRequest.TransactionReason.IMPORT_NEW,
                null,
                List.of(item)
        );

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("items[0].quantity", "expiryDateValid");
    }

    @Test
    void acceptsValidImportAndExport() {
        var item = new CreateInventoryTransactionRequest.Item(1L, "LOT-1", BigDecimal.ONE, LocalDate.now().plusDays(1), BigDecimal.ONE);
        var importRequest = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.IMPORT,
                CreateInventoryTransactionRequest.TransactionReason.IMPORT_NEW,
                null,
                List.of(item)
        );
        var exportRequest = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.EXPORT,
                CreateInventoryTransactionRequest.TransactionReason.EXPORT_ADJUST,
                null,
                List.of(item)
        );

        assertThat(validator.validate(importRequest)).isEmpty();
        assertThat(validator.validate(exportRequest)).isEmpty();
    }

    @Test
    void rejectsNegativeCostMissingWasteReasonAndTooManyItems() {
        var item = new CreateInventoryTransactionRequest.Item(
                1L,
                "LOT-1",
                BigDecimal.ONE,
                null,
                new BigDecimal("-0.001")
        );
        var request = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.EXPORT,
                CreateInventoryTransactionRequest.TransactionReason.EXPORT_WASTE,
                " ",
                java.util.Collections.nCopies(101, item)
        );

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("items", "items[0].costPerUnit", "wasteReasonValid");
    }

    @Test
    void rejectsInvalidIngredientRangeAndLegacyTransactionShapes() {
        var ingredient = new CreateIngredientRequest(
                "CODE",
                "Sữa",
                "lít",
                "Đồ uống",
                new BigDecimal("10"),
                new BigDecimal("5"),
                BigDecimal.ZERO
        );
        var inventoryIn = new InventoryInRequest(
                0L,
                new BigDecimal("1.0001"),
                LocalDate.now().minusDays(1)
        );
        var inventoryOut = new InventoryOutRequest(-1L, BigDecimal.ZERO);

        assertThat(validator.validate(ingredient)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("stockRangeValid");
        assertThat(validator.validate(inventoryIn)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("ingredientId", "quantity", "expiryDate");
        assertThat(validator.validate(inventoryOut)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("ingredientId", "quantity");
    }
}
