package vn.inventoryai.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProphetForecastClientTest {

    private WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    private ProphetForecastClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        webClientBuilder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        client = new ProphetForecastClient(webClientBuilder);
        ReflectionTestUtils.setField(client, "prophetServiceUrl", "http://localhost:8001");
        ReflectionTestUtils.setField(client, "timeoutSeconds", 2);
    }

    @Test
    void returnsEmptyOptionalWhenHistoryIsEmpty() {
        Optional<ProphetForecastClient.ProphetResult> result = client.forecast(
                1L, 2L, Collections.emptyList(), 7, 10.0, 106.0
        );
        assertThat(result).isEmpty();
    }

    @Test
    void successfullyParsesResponseFromProphet() {
        var rawPoint = new ProphetForecastClient.DailyForecastPointRaw(
                "2026-07-21", 5.5, 4.0, 7.0, "Nắng", 32.0, 0.0, 1.0, false, false
        );
        var mockResponse = new ProphetForecastClient.ProphetResponse(
                1L, 2L, 7, 38.5, List.of(rawPoint), "prophet", 45, 8.5, "Good model"
        );

        when(responseSpec.bodyToMono(ProphetForecastClient.ProphetResponse.class))
                .thenReturn(Mono.just(mockResponse));

        var historyPoint = new ProphetForecastClient.ConsumptionPoint(LocalDate.parse("2026-07-20"), 4.0);

        Optional<ProphetForecastClient.ProphetResult> result = client.forecast(
                1L, 2L, List.of(historyPoint), 7, 10.823, 106.629
        );

        assertThat(result).isPresent();
        var prophetResult = result.get();
        assertThat(prophetResult.totalPredictedDemand()).isEqualTo(38.5);
        assertThat(prophetResult.modelUsed()).isEqualTo("prophet");
        assertThat(prophetResult.historyDaysUsed()).isEqualTo(45);
        assertThat(prophetResult.modelAccuracyMape()).isEqualTo(8.5);
        assertThat(prophetResult.confidenceNote()).isEqualTo("Good model");
        assertThat(prophetResult.dailyBreakdown()).hasSize(1);
        assertThat(prophetResult.dailyBreakdown().get(0).date()).isEqualTo(LocalDate.parse("2026-07-21"));
        assertThat(prophetResult.dailyBreakdown().get(0).weatherCondition()).isEqualTo("Nắng");
    }

    @Test
    void handlesHttpErrorsByReturningEmptyOptional() {
        when(responseSpec.bodyToMono(ProphetForecastClient.ProphetResponse.class))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

        var historyPoint = new ProphetForecastClient.ConsumptionPoint(LocalDate.parse("2026-07-20"), 4.0);

        Optional<ProphetForecastClient.ProphetResult> result = client.forecast(
                1L, 2L, List.of(historyPoint), 7, 10.823, 106.629
        );

        assertThat(result).isEmpty();
    }

    @Test
    void handlesTimeoutsByReturningEmptyOptional() {
        when(responseSpec.bodyToMono(ProphetForecastClient.ProphetResponse.class))
                .thenReturn(Mono.error(new java.util.concurrent.TimeoutException("Read timeout")));

        var historyPoint = new ProphetForecastClient.ConsumptionPoint(LocalDate.parse("2026-07-20"), 4.0);

        Optional<ProphetForecastClient.ProphetResult> result = client.forecast(
                1L, 2L, List.of(historyPoint), 7, 10.823, 106.629
        );

        assertThat(result).isEmpty();
    }
}
