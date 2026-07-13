package vn.inventoryai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String storeName,
        @Email @NotBlank String email,
        @Size(min = 8, message = "password must be at least 8 characters") String password
) {
}
