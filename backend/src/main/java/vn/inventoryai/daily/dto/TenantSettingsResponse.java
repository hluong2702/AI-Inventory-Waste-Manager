package vn.inventoryai.daily.dto;

import java.math.BigDecimal;

/**
 * DTO trả về cấu hình TenantSettings.
 */
public record TenantSettingsResponse(
        Long tenantId,
        int expiryWarningDays,
        int expiryConsumptionLookbackDays,
        int reorderConsumptionLookbackDays,
        BigDecimal reorderSafetyBufferDays,
        BigDecimal reorderReviewPeriodDays,
        BigDecimal anomalyThresholdPercent,
        BigDecimal anomalyMinAbsoluteQuantity,
        int dailyActionDisplayLimit,
        String dailyActionRefreshCron
) {
}
