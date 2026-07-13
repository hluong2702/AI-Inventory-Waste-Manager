package vn.inventoryai.alert.dto;

import vn.inventoryai.common.enums.AlertType;

import java.time.Instant;

public record AlertResponse(
        Long id,
        Long storeId,
        AlertType type,
        Long itemId,
        String message,
        String status,
        Instant createdAt
) {
}
