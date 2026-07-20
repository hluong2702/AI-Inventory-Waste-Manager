package vn.inventoryai.forecast;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.forecast.dto.AiForecastResponse;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastControllerTest {

    private static final long STORE_ID = 42L;

    private ForecastService forecastService;
    private AiForecastService aiForecastService;
    private ForecastController controller;

    @BeforeEach
    void setUp() {
        forecastService = mock(ForecastService.class);
        aiForecastService = mock(AiForecastService.class);
        controller = new ForecastController(forecastService, aiForecastService);
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void delegatesToForecastServiceForClassicForecast() {
        controller.forecast(10L, 7, 0, 25);
        verify(forecastService).forecast(10L, 7);
    }

    @Test
    void delegatesToForecastServiceAllWhenNoIngredientIdProvided() {
        controller.forecast(null, 7, 0, 25);
        verify(forecastService).forecastAll(7, PageRequest.of(0, 25));
    }

    @Test
    void delegatesToAiForecastServiceForAiForecast() {
        var mockResponse = new AiForecastResponse(
                STORE_ID, 10L, "Sữa tươi", "MILK", "lít",
                35.0, 5.0, 10.0, 5.0, 30.0,
                Collections.emptyList(), "prophet", 90, 8.5,
                "Model high quality", false
        );

        when(aiForecastService.forecastWithAi(10L, 7)).thenReturn(mockResponse);

        var result = controller.aiForecast(10L, 7);

        assertThat(result.ingredientId()).isEqualTo(10L);
        assertThat(result.aiRecommendedOrder()).isEqualTo(30.0);
        assertThat(result.modelUsed()).isEqualTo("prophet");

        verify(aiForecastService).forecastWithAi(10L, 7);
    }
}
