package vn.inventoryai.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import vn.inventoryai.forecast.dto.DailyForecastPoint;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * HTTP Client gọi Python Prophet Forecast Microservice.
 *
 * Thiết kế:
 * - Timeout riêng biệt: connect 3s, read 15s (Prophet cần thời gian fit)
 * - Trả về Optional.empty() khi service không khả dụng (Spring Boot sẽ dùng fallback)
 * - Không throw exception — mọi lỗi đều được log và graceful degrade
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProphetForecastClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.forecast.prophet-service-url:http://localhost:8001}")
    private String prophetServiceUrl;

    @Value("${app.forecast.prophet-timeout-seconds:15}")
    private int timeoutSeconds;

    // ─── Request / Response POJOs (internal, chỉ dùng để map JSON từ Python) ──

    record HistoryPoint(String ds, double y) {}

    record ForecastRequest(
            @JsonProperty("ingredient_id") long ingredientId,
            @JsonProperty("store_id") long storeId,
            List<HistoryPoint> history,
            @JsonProperty("forecast_days") int forecastDays,
            double latitude,
            double longitude
    ) {}

    record DailyForecastPointRaw(
            String date,
            @JsonProperty("predicted_demand") double predictedDemand,
            @JsonProperty("lower_bound") double lowerBound,
            @JsonProperty("upper_bound") double upperBound,
            @JsonProperty("weather_condition") String weatherCondition,
            @JsonProperty("temperature_max") Double temperatureMax,
            @JsonProperty("rain_mm") Double rainMm,
            @JsonProperty("weather_factor") double weatherFactor,
            @JsonProperty("is_holiday") boolean isHoliday,
            @JsonProperty("is_weekend") boolean isWeekend
    ) {}

    record ProphetResponse(
            @JsonProperty("ingredient_id") long ingredientId,
            @JsonProperty("store_id") long storeId,
            @JsonProperty("forecast_days") int forecastDays,
            @JsonProperty("total_predicted_demand") double totalPredictedDemand,
            @JsonProperty("daily_breakdown") List<DailyForecastPointRaw> dailyBreakdown,
            @JsonProperty("model_used") String modelUsed,
            @JsonProperty("history_days_used") int historyDaysUsed,
            @JsonProperty("model_accuracy_mape") Double modelAccuracyMape,
            @JsonProperty("confidence_note") String confidenceNote
    ) {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Wrapper để truyền dữ liệu lịch sử tiêu thụ.
     */
    public record ConsumptionPoint(LocalDate date, double quantity) {}

    /**
     * Kết quả trả về từ Python service — đã được map sang Java types.
     */
    public record ProphetResult(
            double totalPredictedDemand,
            List<DailyForecastPoint> dailyBreakdown,
            String modelUsed,
            int historyDaysUsed,
            Double modelAccuracyMape,
            String confidenceNote
    ) {}

    /**
     * Gọi Python Prophet service.
     *
     * @return Optional.empty() nếu service không khả dụng (→ Java fallback sẽ xử lý)
     */
    public Optional<ProphetResult> forecast(
            long ingredientId,
            long storeId,
            List<ConsumptionPoint> history,
            int forecastDays,
            double latitude,
            double longitude
    ) {
        if (history.isEmpty()) {
            return Optional.empty();
        }

        var historyPoints = history.stream()
                .map(p -> new HistoryPoint(p.date().toString(), p.quantity()))
                .toList();

        var request = new ForecastRequest(
                ingredientId, storeId, historyPoints, forecastDays, latitude, longitude
        );

        try {
            ProphetResponse response = buildWebClient()
                    .post()
                    .uri("/forecast/prophet")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ProphetResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response == null) {
                log.warn("Prophet service returned null response for ingredient_id={}", ingredientId);
                return Optional.empty();
            }

            List<DailyForecastPoint> breakdown = response.dailyBreakdown().stream()
                    .map(raw -> new DailyForecastPoint(
                            LocalDate.parse(raw.date()),
                            raw.predictedDemand(),
                            raw.lowerBound(),
                            raw.upperBound(),
                            raw.weatherCondition(),
                            raw.temperatureMax(),
                            raw.rainMm(),
                            raw.weatherFactor(),
                            raw.isHoliday(),
                            raw.isWeekend()
                    ))
                    .toList();

            return Optional.of(new ProphetResult(
                    response.totalPredictedDemand(),
                    breakdown,
                    response.modelUsed(),
                    response.historyDaysUsed(),
                    response.modelAccuracyMape(),
                    response.confidenceNote()
            ));

        } catch (WebClientResponseException ex) {
            log.warn("Prophet service HTTP {} for ingredient_id={}: {}",
                    ex.getStatusCode(), ingredientId, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            // Bao gồm: timeout, connection refused, network errors
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("timeout") || msg.contains("Timeout"))) {
                log.warn("Prophet service timeout after {}s for ingredient_id={}", timeoutSeconds, ingredientId);
            } else {
                log.warn("Prophet service unavailable for ingredient_id={}: {}", ingredientId, msg);
            }
            return Optional.empty();
        }
    }

    private WebClient buildWebClient() {
        return webClientBuilder
                .baseUrl(prophetServiceUrl)
                .build();
    }
}
