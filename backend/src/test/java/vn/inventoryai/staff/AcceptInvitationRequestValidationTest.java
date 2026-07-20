package vn.inventoryai.staff;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import vn.inventoryai.staff.dto.AcceptInvitationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptInvitationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsControlCharactersInDisplayName() {
        var request = new AcceptInvitationRequest("valid-token", "Staff\r\nInjected", "password123");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("fullName"));
    }
}
