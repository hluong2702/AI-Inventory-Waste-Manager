package vn.inventoryai.store.dto;

import jakarta.validation.constraints.NotBlank;

public record StoreRequest(
        @NotBlank String name,
        @NotBlank String address,
        String phone
) {
}
