package vn.inventoryai.daily.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body để cập nhật TenantSettings. Tất cả field đều bắt buộc
 * để tránh partial-update gây ra giá trị null không mong muốn.
 */
public record UpdateTenantSettingsRequest(

        @NotNull @Min(1) @Max(30)
        Integer expiryWarningDays,

        @NotNull @Min(7) @Max(90)
        Integer expiryConsumptionLookbackDays,

        @NotNull @Min(7) @Max(90)
        Integer reorderConsumptionLookbackDays,

        @NotNull
        @DecimalMin(value = "0.0", message = "Safety buffer không được âm")
        BigDecimal reorderSafetyBufferDays,

        @NotNull
        @DecimalMin(value = "1.0", message = "Review period phải ít nhất 1 ngày")
        BigDecimal reorderReviewPeriodDays,

        @NotNull
        @DecimalMin(value = "5.0", message = "Anomaly threshold tối thiểu 5%")
        BigDecimal anomalyThresholdPercent,

        @NotNull
        @DecimalMin(value = "0.0", message = "Min absolute quantity không được âm")
        BigDecimal anomalyMinAbsoluteQuantity,

        @NotNull @Min(1) @Max(100)
        Integer dailyActionDisplayLimit
) {
}
