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
    void acceptsValidImportAndAdjustment() {
        var item = new CreateInventoryTransactionRequest.Item(1L, "LOT-1", BigDecimal.ONE, LocalDate.now().plusDays(1), BigDecimal.ONE);
        var importRequest = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.IMPORT,
                CreateInventoryTransactionRequest.TransactionReason.IMPORT_NEW,
                null,
                List.of(item)
        );
        var adjustmentRequest = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.ADJUSTMENT,
                CreateInventoryTransactionRequest.TransactionReason.EXPORT_ADJUST,
                null,
                List.of(item)
        );

        assertThat(validator.validate(importRequest)).isEmpty();
        assertThat(validator.validate(adjustmentRequest)).isEmpty();
    }
}
