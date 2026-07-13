package vn.inventoryai.insight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.insight.dto.InventoryInsightResponse;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatch;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransaction;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryInsightServiceTest {
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
                Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("UTC"))
        );
    }

    @Test
    void flagsLowStockAndRecommendsOrderQuantity() {
        Ingredient milk = ingredient(1L, "Sữa tươi", "hộp", "30");
        stubIngredient(milk);
        stubStock(milk, "10", List.of(batch(11L, milk, "10", LocalDate.parse("2026-07-30"))));
        stubUsage(milk, dailyUsage(milk, "8", 7));

        InventoryInsightResponse insight = service.inventoryInsights(99L).getFirst();

        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.HIGH);
        assertThat(insight.daysUntilStockout()).isEqualByComparingTo("1.3");
        assertThat(insight.recommendedOrderQty()).isEqualByComparingTo("76");
        assertThat(insight.explanationBullets()).anySatisfy(bullet ->
                assertThat(bullet).contains("Tồn hiện tại 10", "mức an toàn 30"));
    }

    @Test
    void flagsOverstockWithoutOrderingMore() {
        Ingredient coffee = ingredient(2L, "Cà phê", "kg", "20");
        stubIngredient(coffee);
        stubStock(coffee, "220", List.of(batch(21L, coffee, "220", LocalDate.parse("2026-12-31"))));
        stubUsage(coffee, dailyUsage(coffee, "2", 28));

        InventoryInsightResponse insight = service.inventoryInsights(99L).getFirst();

        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.HIGH);
        assertThat(insight.daysUntilStockout()).isEqualByComparingTo("110");
        assertThat(insight.recommendedOrderQty()).isEqualByComparingTo("0");
        assertThat(insight.explanationBullets()).contains("Chưa cần nhập thêm theo mục tiêu 7 ngày bán + tồn an toàn.");
    }

    @Test
    void flagsExpiringSoonBatch() {
        Ingredient pearl = ingredient(3L, "Trân châu", "kg", "15");
        stubIngredient(pearl);
        stubStock(pearl, "30", List.of(batch(31L, pearl, "30", LocalDate.parse("2026-07-15"))));
        stubUsage(pearl, dailyUsage(pearl, "3", 7));

        InventoryInsightResponse insight = service.inventoryInsights(99L).getFirst();

        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.HIGH);
        assertThat(insight.daysUntilExpiry()).isEqualTo(2);
        assertThat(insight.nearestBatchId()).isEqualTo(31L);
        assertThat(insight.ctaLabel()).isEqualTo("Ưu tiên dùng trước");
    }

    @Test
    void handlesInsufficientHistoryWithoutWeekdayAdjustment() {
        Ingredient syrup = ingredient(4L, "Syrup", "chai", "10");
        stubIngredient(syrup);
        stubStock(syrup, "20", List.of(batch(41L, syrup, "20", LocalDate.parse("2026-10-01"))));
        stubUsage(syrup, dailyUsage(syrup, "1", 3));

        InventoryInsightResponse insight = service.inventoryInsights(99L).getFirst();

        assertThat(insight.weekdayAdjustedUsage()).isNull();
        assertThat(insight.wasteRiskLevel()).isEqualTo(WasteRiskLevel.LOW);
        assertThat(insight.explanationBullets()).contains("Chưa đủ lịch sử để điều chỉnh theo thứ trong tuần.");
    }

    private void stubIngredient(Ingredient ingredient) {
        when(ingredientRepository.findByStoreIdAndDeletedFalse(99L)).thenReturn(List.of(ingredient));
    }

    private void stubStock(Ingredient ingredient, String currentStock, List<InventoryBatch> batches) {
        when(batchRepository.sumAvailable(99L, ingredient.getId())).thenReturn(new BigDecimal(currentStock));
        when(batchRepository.findByStoreIdAndIngredientIdAndQuantityGreaterThanOrderByExpiryDateAsc(
                eq(99L),
                eq(ingredient.getId()),
                any(BigDecimal.class)
        )).thenReturn(batches);
    }

    private void stubUsage(Ingredient ingredient, List<StockTransaction> transactions) {
        when(transactionRepository.findByStoreIdAndIngredientIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                eq(99L),
                eq(ingredient.getId()),
                eq(StockTransactionType.OUT),
                any(Instant.class)
        )).thenReturn(transactions);
    }

    private Ingredient ingredient(Long id, String name, String unit, String minStock) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(id);
        ingredient.setName(name);
        ingredient.setUnit(unit);
        ingredient.setMinStock(new BigDecimal(minStock));
        return ingredient;
    }

    private InventoryBatch batch(Long id, Ingredient ingredient, String quantity, LocalDate expiryDate) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(id);
        batch.setIngredient(ingredient);
        batch.setQuantity(new BigDecimal(quantity));
        batch.setExpiryDate(expiryDate);
        return batch;
    }

    private List<StockTransaction> dailyUsage(Ingredient ingredient, String dailyQuantity, int days) {
        return java.util.stream.IntStream.rangeClosed(1, days)
                .mapToObj(dayOffset -> transaction(
                        ingredient,
                        dailyQuantity,
                        Instant.parse("2026-07-13T12:00:00Z").minusSeconds(dayOffset * 24L * 60L * 60L)
                ))
                .toList();
    }

    private StockTransaction transaction(Ingredient ingredient, String quantity, Instant createdAt) {
        StockTransaction tx = new StockTransaction();
        tx.setIngredient(ingredient);
        tx.setType(StockTransactionType.OUT);
        tx.setQuantity(new BigDecimal(quantity));
        tx.setCreatedAt(createdAt);
        return tx;
    }
}
