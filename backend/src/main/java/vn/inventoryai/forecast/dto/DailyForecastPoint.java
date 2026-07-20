package vn.inventoryai.forecast.dto;

import java.time.LocalDate;

/**
 * Dự báo nhu cầu cho một ngày cụ thể — breakdown từ Python Prophet service.
 */
public record DailyForecastPoint(
        LocalDate date,

        /** Nhu cầu dự báo (đã tích hợp weather adjustment). */
        double predictedDemand,

        /** Giới hạn dưới 80% confidence interval. */
        double lowerBound,

        /** Giới hạn trên 80% confidence interval. */
        double upperBound,

        /** Mô tả thời tiết tiếng Việt (ví dụ: "Mưa vừa, 28°C"). */
        String weatherCondition,

        /** Nhiệt độ tối đa dự báo (°C). Null nếu không có dữ liệu thời tiết. */
        Double temperatureMax,

        /** Lượng mưa dự báo (mm). Null nếu không có dữ liệu thời tiết. */
        Double rainMm,

        /**
         * Hệ số điều chỉnh nhu cầu do thời tiết.
         * 1.0 = bình thường; >1.0 = nhu cầu tăng; <1.0 = nhu cầu giảm.
         */
        double weatherFactor,

        /** True nếu ngày này là ngày lễ Việt Nam. */
        boolean isHoliday,

        /** True nếu là cuối tuần (Thứ 7, Chủ nhật). */
        boolean isWeekend
) {
}
