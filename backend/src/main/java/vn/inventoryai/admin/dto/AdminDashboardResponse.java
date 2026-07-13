package vn.inventoryai.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        long totalStores,
        long totalActiveUsers,
        BigDecimal mrr,
        long storesExpiringSoon,
        long newStoresToday,
        List<StoreActivityResponse> mostActiveStores
) {
}
