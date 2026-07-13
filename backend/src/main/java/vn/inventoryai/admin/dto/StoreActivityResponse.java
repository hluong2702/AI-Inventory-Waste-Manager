package vn.inventoryai.admin.dto;

public record StoreActivityResponse(
        Long storeId,
        String storeName,
        long transactionCount
) {
}
