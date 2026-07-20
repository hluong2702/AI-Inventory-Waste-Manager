package vn.inventoryai.insight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.inventoryai.insight.dto.InventoryInsightResponse;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryInsightServiceTest {
    private static final long STORE_ID = 99L;
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-07-13");

    private IngredientRepository ingredientRepository;
    private InventoryBatchRepository batchRepository;
    private StockTransactionRepository transactionRepository;
    private InventoryInsightService service;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        batchRepository = mock(InventoryBatchRepository.class);
        transactionRepository = mock(StockTransactionRepository.class);
        service = new InventoryInsightService(
                ingredientRepository,
                batchRepository,
                transactionRepository,
                Clock.fixed(NOW, ZoneId.of("UTC"))
        );
    }

    @Test
    void flagsLowStockAndRecommendsOrderQuantity() {
        Ingredient milk = ingredient(1L, "Sữa tươi", "hộp", "30");
        stubIngredientPage(milk);
        stubAggregates(
                usage(milk.getId(), "56", "56", 7, "8", 1),
                stock(milk.getId(), "10"),
                nearest(milk.getId(), 11L, "10", LocalDate.parse("2026-07-30"))
        );

        InventoryInsightResponse insight = firstInsight();

        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.HIGH);
        assertThat(insight.daysUntilStockout()).isEqualByComparingTo("1.3");
        assertThat(insight.recommendedOrderQty()).isEqualByComparingTo("76");
        assertThat(insight.explanationBullets()).anySatisfy(bullet ->
                assertThat(bullet).contains("Tồn hiện tại 10", "mức an toàn 30"));
    }

    @Test
    void flagsOverstockWithoutOrderingMore() {
        Ingredient coffee = ingredient(2L, "Cà phê", "kg", "20");
        stubIngredientPage(coffee);
        stubAggregates(
                usage(coffee.getId(), "14", "56", 28, "8", 4),
                stock(coffee.getId(), "220"),
                nearest(coffee.getId(), 21L, "220", LocalDate.parse("2026-12-31"))
        );

        InventoryInsightResponse insight = firstInsight();

        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.HIGH);
        assertThat(insight.daysUntilStockout()).isEqualByComparingTo("110");
        assertThat(insight.recommendedOrderQty()).isEqualByComparingTo("0");
        assertThat(insight.explanationBullets()).contains("Chưa cần nhập thêm theo mục tiêu 7 ngày bán + tồn an toàn.");
    }

    @Test
    void flagsNearestSellableExpiringBatch() {
        Ingredient pearl = ingredient(3L, "Trân châu", "kg", "15");
        stubIngredientPage(pearl);
        stubAggregates(
                usage(pearl.getId(), "21", "21", 7, "3", 1),
                stock(pearl.getId(), "30"),
                nearest(pearl.getId(), 31L, "30", LocalDate.parse("2026-07-15"))
        );

        InventoryInsightResponse insight = firstInsight();

        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.HIGH);
        assertThat(insight.daysUntilExpiry()).isEqualTo(2);
        assertThat(insight.nearestBatchId()).isEqualTo(31L);
        assertThat(insight.ctaLabel()).isEqualTo("Ưu tiên dùng trước");
    }

    @Test
    void handlesInsufficientHistoryWithoutWeekdayAdjustment() {
        Ingredient syrup = ingredient(4L, "Syrup", "chai", "10");
        stubIngredientPage(syrup);
        stubAggregates(
                usage(syrup.getId(), "3", "3", 3, "1", 1),
                stock(syrup.getId(), "20"),
                nearest(syrup.getId(), 41L, "20", LocalDate.parse("2026-10-01"))
        );

        InventoryInsightResponse insight = firstInsight();

        assertThat(insight.weekdayAdjustedUsage()).isNull();
        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.LOW);
        assertThat(insight.explanationBullets()).contains("Chưa đủ lịch sử để điều chỉnh theo thứ trong tuần.");
    }

    @Test
    void usesThreeSetBasedTenantQueriesForEntireBoundedPage() {
        Ingredient first = ingredient(10L, "A", "kg", "1");
        Ingredient second = ingredient(11L, "B", "kg", "1");
        Ingredient third = ingredient(12L, "C", "kg", "1");
        when(ingredientRepository.findByStoreIdAndDeletedFalse(eq(STORE_ID), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(first, second, third),
                        invocation.getArgument(1),
                        203
                ));
        when(transactionRepository.aggregateConsumptionMetrics(
                eq(STORE_ID),
                argThat(ids -> ids.equals(List.of(10L, 11L, 12L))),
                any(Instant.class),
                any(Instant.class),
                eq(2),
                eq("+00:00")
        )).thenReturn(List.of());
        when(batchRepository.sumSellableGroupedByIngredientIds(
                eq(STORE_ID),
                argThat(ids -> ids.equals(List.of(10L, 11L, 12L))),
                eq(TODAY)
        )).thenReturn(List.of());
        when(batchRepository.findNearestSellableBatchByIngredientIds(
                eq(STORE_ID),
                argThat(ids -> ids.equals(List.of(10L, 11L, 12L))),
                eq(TODAY)
        )).thenReturn(List.of());

        var page = service.inventoryInsights(STORE_ID, PageRequest.of(2, 500));

        assertThat(page.getContent()).hasSize(3);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ingredientRepository).findByStoreIdAndDeletedFalse(eq(STORE_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(InventoryInsightService.MAX_PAGE_SIZE);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("name")).isNotNull();
        verify(transactionRepository, times(1)).aggregateConsumptionMetrics(
                eq(STORE_ID),
                any(),
                eq(Instant.parse("2026-07-07T00:00:00Z")),
                eq(Instant.parse("2026-06-16T00:00:00Z")),
                eq(2),
                eq("+00:00")
        );
        verify(batchRepository, times(1)).sumSellableGroupedByIngredientIds(
                eq(STORE_ID),
                any(),
                eq(TODAY)
        );
        verify(batchRepository, times(1)).findNearestSellableBatchByIngredientIds(
                eq(STORE_ID),
                any(),
                eq(TODAY)
        );
    }

    @Test
    void skipsAllAggregateQueriesForEmptyPage() {
        when(ingredientRepository.findByStoreIdAndDeletedFalse(eq(STORE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        var page = service.inventoryInsights(STORE_ID, PageRequest.of(0, 25));

        assertThat(page).isEmpty();
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(batchRepository);
    }

    private InventoryInsightResponse firstInsight() {
        return service.inventoryInsights(STORE_ID, PageRequest.of(0, 25)).getContent().getFirst();
    }

    private void stubIngredientPage(Ingredient... ingredients) {
        when(ingredientRepository.findByStoreIdAndDeletedFalse(eq(STORE_ID), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        Arrays.asList(ingredients),
                        invocation.getArgument(1),
                        ingredients.length
                ));
    }

    private void stubAggregates(
            StockTransactionRepository.IngredientUsageMetrics usage,
            InventoryBatchRepository.IngredientQuantity stock,
            InventoryBatchRepository.NearestSellableBatch nearest
    ) {
        when(transactionRepository.aggregateConsumptionMetrics(
                eq(STORE_ID),
                any(),
                any(Instant.class),
                any(Instant.class),
                any(Integer.class),
                any(String.class)
        )).thenReturn(List.of(usage));
        when(batchRepository.sumSellableGroupedByIngredientIds(
                eq(STORE_ID),
                any(),
                any(LocalDate.class)
        )).thenReturn(List.of(stock));
        when(batchRepository.findNearestSellableBatchByIngredientIds(
                eq(STORE_ID),
                any(),
                any(LocalDate.class)
        )).thenReturn(List.of(nearest));
    }

    private Ingredient ingredient(Long id, String name, String unit, String minStock) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(id);
        ingredient.setName(name);
        ingredient.setCode("ING-" + id);
        ingredient.setUnit(unit);
        ingredient.setMinStock(new BigDecimal(minStock));
        return ingredient;
    }

    private StockTransactionRepository.IngredientUsageMetrics usage(
            Long ingredientId,
            String quantity7d,
            String quantity28d,
            long historyDayCount,
            String weekdayQuantity,
            long weekdayDayCount
    ) {
        StockTransactionRepository.IngredientUsageMetrics row = mock(StockTransactionRepository.IngredientUsageMetrics.class);
        when(row.getIngredientId()).thenReturn(ingredientId);
        when(row.getQuantity7d()).thenReturn(new BigDecimal(quantity7d));
        when(row.getQuantity28d()).thenReturn(new BigDecimal(quantity28d));
        when(row.getHistoryDayCount()).thenReturn(historyDayCount);
        when(row.getWeekdayQuantity()).thenReturn(new BigDecimal(weekdayQuantity));
        when(row.getWeekdayDayCount()).thenReturn(weekdayDayCount);
        return row;
    }

    private InventoryBatchRepository.IngredientQuantity stock(Long ingredientId, String quantity) {
        InventoryBatchRepository.IngredientQuantity row = mock(InventoryBatchRepository.IngredientQuantity.class);
        when(row.getIngredientId()).thenReturn(ingredientId);
        when(row.getQuantity()).thenReturn(new BigDecimal(quantity));
        return row;
    }

    private InventoryBatchRepository.NearestSellableBatch nearest(
            Long ingredientId,
            Long batchId,
            String quantity,
            LocalDate expiryDate
    ) {
        InventoryBatchRepository.NearestSellableBatch row = mock(InventoryBatchRepository.NearestSellableBatch.class);
        when(row.getIngredientId()).thenReturn(ingredientId);
        when(row.getBatchId()).thenReturn(batchId);
        when(row.getQuantity()).thenReturn(new BigDecimal(quantity));
        when(row.getExpiryDate()).thenReturn(expiryDate);
        return row;
    }
}
