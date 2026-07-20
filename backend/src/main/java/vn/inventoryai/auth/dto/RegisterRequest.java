package vn.inventoryai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 180) String storeName,
        @Email @NotBlank @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 128, message = "password must contain between 8 and 128 characters") String password
) {
}
