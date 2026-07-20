package vn.inventoryai.forecast;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.forecast.dto.DailyForecastPoint;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiForecastServiceTest {

    private static final Long STORE_ID = 99L;
    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-07-14");

    private IngredientRepository ingredientRepository;
    private InventoryBatchRepository batchRepository;
    private StockTransactionRepository transactionRepository;
    private StoreRepository storeRepository;
    private ProphetForecastClient prophetClient;
    private Clock clock;

    private AiForecastService service;
    private Ingredient milk;
    private Store store;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        batchRepository = mock(InventoryBatchRepository.class);
        transactionRepository = mock(StockTransactionRepository.class);
        storeRepository = mock(StoreRepository.class);
        prophetClient = mock(ProphetForecastClient.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new AiForecastService(
                ingredientRepository,
                batchRepository,
                transactionRepository,
                storeRepository,
                prophetClient,
                clock
        );

        milk = new Ingredient();
        milk.setId(1L);
        milk.setCode("MILK-FRESH");
        milk.setName("Sữa tươi");
        milk.setUnit("lít");
        milk.setMinStock(new BigDecimal("10"));

        store = new Store();
        store.setId(STORE_ID);
        store.setLatitude(new BigDecimal("10.823"));
        store.setLongitude(new BigDecimal("106.629"));

        UserPrincipal principal = new UserPrincipal(7L, STORE_ID, "manager@example.com", Role.MANAGER, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
        TenantContext.setStoreId(STORE_ID);

        when(ingredientRepository.findByIdAndStoreIdAndDeletedFalse(1L, STORE_ID))
                .thenReturn(Optional.of(milk));
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(batchRepository.sumSellable(eq(STORE_ID), eq(1L), any(LocalDate.class)))
                .thenReturn(new BigDecimal("5")); // Tồn kho hiện tại: 5 lít
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfullyReturnsAiForecastWhenProphetClientSucceeds() {
        var dailyPoint = new DailyForecastPoint(
                TODAY.plusDays(1), 4.0, 3.0, 5.0, "Nắng", 32.0, 0.0, 1.0, false, false
        );
        var mockResult = new ProphetForecastClient.ProphetResult(
                28.0, List.of(dailyPoint), "prophet", 90, 5.2, "AI Model is highly confident"
        );

        when(prophetClient.forecast(
                eq(1L), eq(STORE_ID), any(), eq(7), eq(10.823), eq(106.629)
        )).thenReturn(Optional.of(mockResult));

        var response = service.forecastWithAi(1L, 7);

        assertThat(response.ingredientId()).isEqualTo(1L);
        assertThat(response.ingredientName()).isEqualTo("Sữa tươi");
        assertThat(response.totalPredictedDemand()).isEqualTo(28.0);
        assertThat(response.avgDailyPredicted()).isEqualTo(28.0); // 1 point -> avg = 28
        assertThat(response.currentStock()).isEqualTo(5.0);
        // aiRecommendedOrder = totalPredictedDemand (28) + minStock (10) - currentStock (5) = 33
        assertThat(response.aiRecommendedOrder()).isEqualTo(33.0);
        assertThat(response.modelUsed()).isEqualTo("prophet");
        assertThat(response.confidenceNote()).contains("highly confident");
        assertThat(response.isJavaFallback()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToJavaMovingAverageWhenProphetClientReturnsEmpty() {
        when(prophetClient.forecast(
                anyLong(), anyLong(), any(), anyInt(), anyDouble(), anyDouble()
        )).thenReturn(Optional.empty()); // Client error/offline

        when(transactionRepository.findByStoreIdAndIngredientIdAndTypeAndReasonAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                eq(STORE_ID), eq(1L), eq(StockTransactionType.OUT), eq("EXPORT_CONSUME"), any()
        )).thenReturn(Collections.emptyList()); // No consumption history

        var response = service.forecastWithAi(1L, 7);

        assertThat(response.ingredientId()).isEqualTo(1L);
        assertThat(response.modelUsed()).isEqualTo("moving_average");
        assertThat(response.totalPredictedDemand()).isEqualTo(0.0); // No history -> total predicted = 0
        // recommended = total (0) + minStock (10) - currentStock (5) = 5
        assertThat(response.aiRecommendedOrder()).isEqualTo(5.0);
        assertThat(response.isJavaFallback()).isTrue();
        assertThat(response.confidenceNote()).contains("không khả dụng");
    }
}
