package vn.inventoryai.admin.dto;

import jakarta.validation.constraints.NotNull;
import vn.inventoryai.common.enums.StoreStatus;

public record UpdateStoreStatusRequest(
        @NotNull StoreStatus status
) {
}
