package vn.inventoryai.subscription.dto;

public record CancelSubscriptionRequest(
        boolean cancelAutoRenew
) {
}
