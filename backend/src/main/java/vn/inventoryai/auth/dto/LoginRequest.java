package vn.inventoryai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank @jakarta.validation.constraints.Size(max = 180) String email,
        @NotBlank @jakarta.validation.constraints.Size(max = 128) String password
) {
}
