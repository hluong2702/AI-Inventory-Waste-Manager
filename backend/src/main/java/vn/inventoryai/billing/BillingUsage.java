package vn.inventoryai.billing;

public record BillingUsage(
        long stores,
        long staff,
        long ingredients
) {
}
