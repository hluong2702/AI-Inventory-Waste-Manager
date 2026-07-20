package vn.inventoryai.forecast;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.forecast.dto.AiForecastResponse;
import vn.inventoryai.forecast.dto.ForecastResponse;
import vn.inventoryai.subscription.feature.RequiresFeature;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Validated
public class ForecastController {
    private final ForecastService forecastService;
    private final AiForecastService aiForecastService;

    /**
     * Dự báo Moving Average (thuật toán cũ). Vẫn giữ cho backward compat.
     * Feature gate: BASIC_FORECAST (gói FREE trở lên).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @RequiresFeature("BASIC_FORECAST")
    Object forecast(
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        if (ingredientId == null) {
            return forecastService.forecastAll(days, PageRequest.of(page, size));
        }
        return forecastService.forecast(ingredientId, days);
    }

    /**
     * Dự báo AI (Prophet + Weather). Yêu cầu ingredientId cụ thể.
     * Feature gate: ADVANCED_FORECAST (gói PRO trở lên).
     *
     * Nếu Python Prophet service không khả dụng, trả về Moving Average fallback
     * với trường isJavaFallback=true để Frontend hiển thị thông báo phù hợp.
     */
    @GetMapping("/ai")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @RequiresFeature("ADVANCED_FORECAST")
    AiForecastResponse aiForecast(
            @RequestParam Long ingredientId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days
    ) {
        return aiForecastService.forecastWithAi(ingredientId, days);
    }
}
