package vn.inventoryai.forecast;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.forecast.dto.ForecastResponse;
import vn.inventoryai.subscription.feature.RequiresFeature;

import java.util.List;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {
    private final ForecastService forecastService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @RequiresFeature("BASIC_FORECAST")
    Object forecast(@RequestParam(required = false) Long ingredientId, @RequestParam(defaultValue = "7") int days) {
        if (ingredientId == null) {
            List<ForecastResponse> forecasts = forecastService.forecastAll(days);
            return forecasts;
        }
        return forecastService.forecast(ingredientId, days);
    }
}
