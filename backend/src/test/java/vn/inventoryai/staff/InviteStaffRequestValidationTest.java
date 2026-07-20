package vn.inventoryai.staff;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.staff.dto.InviteStaffRequest;

import static org.assertj.core.api.Assertions.assertThat;

class InviteStaffRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankMalformedAndOversizedEmail() {
        assertThat(validator.validate(new InviteStaffRequest("", Role.STAFF))).isNotEmpty();
        assertThat(validator.validate(new InviteStaffRequest("not-an-email", Role.STAFF))).isNotEmpty();
        assertThat(validator.validate(new InviteStaffRequest("a".repeat(171) + "@coffee.vn", Role.STAFF))).isNotEmpty();
    }

    @Test
    void acceptsValidInviteRequest() {
        assertThat(validator.validate(new InviteStaffRequest("staff@coffee.vn", Role.MANAGER))).isEmpty();
    }
}
