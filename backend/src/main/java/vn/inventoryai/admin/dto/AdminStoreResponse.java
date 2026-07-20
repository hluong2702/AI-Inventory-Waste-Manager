package vn.inventoryai.admin.dto;

import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.time.Instant;

public record AdminStoreResponse(
        Long id,
        String name,
        String address,
        String phone,
        SubscriptionPlan subscriptionPlan,
        StoreStatus status,
        Instant createdAt
) {
}
