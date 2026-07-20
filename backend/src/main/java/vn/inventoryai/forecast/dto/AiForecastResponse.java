package vn.inventoryai.forecast.dto;

import java.util.List;

/**
 * Response đầy đủ từ AI Forecast endpoint — bao gồm Prophet breakdown + weather.
 * Được trả về tại GET /api/forecast/ai?ingredientId=&days=
 */
public record AiForecastResponse(
        Long storeId,
        Long ingredientId,
        String ingredientName,
        String ingredientCode,
        String unit,

        /** Tổng nhu cầu dự báo trong kỳ (sum of dailyBreakdown.predictedDemand). */
        double totalPredictedDemand,

        /** Nhu cầu dự báo trung bình mỗi ngày. */
        double avgDailyPredicted,

        /** Tồn kho hiện tại. */
        double currentStock,

        /** Mức tồn tối thiểu an toàn. */
        double minStock,

        /**
         * Lượng đề xuất đặt hàng = max(0, totalPredictedDemand + minStock − currentStock).
         * Đây là giá trị AI đã điều chỉnh theo thời tiết và ngày lễ.
         */
        double aiRecommendedOrder,

        /** Breakdown dự báo từng ngày (kèm thời tiết, holiday). */
        List<DailyForecastPoint> dailyBreakdown,

        /**
         * Model sử dụng: "prophet" hoặc "moving_average" (fallback khi <30 ngày lịch sử).
         */
        String modelUsed,

        /** Số ngày lịch sử thực sự được đưa vào model. */
        int historyDaysUsed,

        /**
         * Mean Absolute Percentage Error (%). Null nếu không đủ data cross-validate.
         * Giá trị thấp = mô hình chính xác hơn.
         */
        Double modelAccuracyMape,

        /** Ghi chú về nguồn dữ liệu và độ tin cậy của dự báo. */
        String confidenceNote,

        /**
         * True nếu Python Prophet service không khả dụng và response là Moving Average fallback
         * từ tầng Spring Boot (không phải từ Python service).
         */
        boolean isJavaFallback
) {
}
