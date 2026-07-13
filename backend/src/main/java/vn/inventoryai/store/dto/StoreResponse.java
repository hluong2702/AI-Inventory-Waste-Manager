package vn.inventoryai.store.dto;

import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;

public record StoreResponse(
        Long id,
        String name,
        String address,
        String phone,
        SubscriptionPlan subscriptionPlan,
        StoreStatus status
) {
}
