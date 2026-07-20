package vn.inventoryai.daily.dto;

import vn.inventoryai.daily.DailyActionStatus;
import vn.inventoryai.daily.DailyActionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO trả về cho client khi liệt kê / xem chi tiết Daily Actions.
 */
public record DailyActionResponse(
        Long id,
        Long tenantId,
        DailyActionType actionType,

        /* Thông tin nguyên liệu */
        Long productId,
        String productName,
        String productCode,
        String productUnit,

        /* Thông tin lô hàng (nullable — chỉ có với EXPIRY_RISK) */
        Long batchId,
        String batchNumber,
        LocalDate batchExpiryDate,

        /* Nội dung hiển thị */
        String title,
        String description,

        /* Số liệu rủi ro */
        BigDecimal riskQtyMin,
        BigDecimal riskQtyMax,
        BigDecimal riskValueEstimate,

        /* Điểm ưu tiên */
        BigDecimal priorityScore,

        /* Trạng thái */
        DailyActionStatus status,

        /* Timestamps */
        Instant computedAt,
        Instant expiresAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant dismissedAt,
        String dismissReason,
        Instant createdAt
) {
}
