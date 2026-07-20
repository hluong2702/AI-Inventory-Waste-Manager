package vn.inventoryai.daily;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatch;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.store.Store;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyActionComputationServiceTest {

    private static final long STORE_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-07-14T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-07-14");

    // Fixed clock — không phụ thuộc vào thời gian thực
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DailyActionRepository dailyActionRepository;
    private TenantSettingsRepository tenantSettingsRepository;
    private IngredientRepository ingredientRepository;
    private InventoryBatchRepository batchRepository;
    private StockTransactionRepository transactionRepository;

    private DailyActionComputationService service;

    @BeforeEach
    void setUp() {
        dailyActionRepository = mock(DailyActionRepository.class);
        tenantSettingsRepository = mock(TenantSettingsRepository.class);
        ingredientRepository = mock(IngredientRepository.class);
        batchRepository = mock(InventoryBatchRepository.class);
        transactionRepository = mock(StockTransactionRepository.class);

        service = new DailyActionComputationService(
                dailyActionRepository,
                tenantSettingsRepository,
                ingredientRepository,
                batchRepository,
                transactionRepository,
                clock
        );

        // Default: không có settings riêng → dùng mặc định
        when(tenantSettingsRepository.findByTenantId(STORE_ID)).thenReturn(Optional.empty());

        // Default auto-resolve trả về 0 (không có gì để resolve)
        when(dailyActionRepository.resolveExpiredActions(anyLong(), any())).thenReturn(0);
        when(dailyActionRepository.autoResolveStaleExpiryRisk(anyLong(), any())).thenReturn(0);
        when(dailyActionRepository.autoResolveStaleReorder(anyLong(), any())).thenReturn(0);
        when(dailyActionRepository.deleteStaleClosedActions(anyLong(), any())).thenReturn(0);
    }

    // ─── EXPIRY_RISK ──────────────────────────────────────────────────────────

    @Test
    void createsExpiryRiskActionForBatchExpiringInWarningWindow() {
        Ingredient milk = ingredient(1L, "Sữa tươi", "hộp", "0", "50");
        InventoryBatch batch = batch(101L, milk, TODAY.plusDays(2), "10.000", "25000.00");

        // Batch sắp hết hạn trong 3 ngày (ngưỡng mặc định)
        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                eq(STORE_ID), eq(BigDecimal.ZERO), eq(TODAY.plusDays(3))
        )).thenReturn(List.of(batch));

        when(batchRepository.sumSellableGroupedByIngredientIds(
                eq(STORE_ID), any(), eq(TODAY)
        )).thenReturn(List.of(stockRow(1L, "10")));

        // Không có hành động OPEN trước đó → tạo mới
        when(dailyActionRepository.findOpenByRecomputeKey(
                eq(STORE_ID), eq(DailyActionType.EXPIRY_RISK), eq(1L), eq(101L)
        )).thenReturn(Optional.empty());

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(List.of(milk));
        when(batchRepository.sumSellableGroupedByIngredientIds(
                eq(STORE_ID), anyList(), eq(TODAY)
        )).thenReturn(List.of(stockRow(1L, "10")));
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                anyLong(), anyList(), any(), anyString(), any()
        )).thenReturn(Collections.emptyList());

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.expiryRiskCreated()).isEqualTo(1);
        assertThat(result.expiryRiskUpdated()).isEqualTo(0);

        // Kiểm tra action được lưu với đúng các field
        var captor = org.mockito.ArgumentCaptor.forClass(DailyAction.class);
        verify(dailyActionRepository, atLeastOnce()).save(captor.capture());

        DailyAction saved = captor.getAllValues().stream()
                .filter(a -> a.getActionType() == DailyActionType.EXPIRY_RISK)
                .findFirst()
                .orElseThrow();

        assertThat(saved.getTenantId()).isEqualTo(STORE_ID);
        assertThat(saved.getStatus()).isEqualTo(DailyActionStatus.OPEN);
        assertThat(saved.getPriorityScore()).isPositive();
        assertThat(saved.getTitle()).contains("2 ngày");
        assertThat(saved.getRiskValueEstimate())
                .isEqualByComparingTo(new BigDecimal("250000.00")); // 10 * 25000
    }

    @Test
    void updatesExistingOpenExpiryRiskRatherThanCreatingDuplicate() {
        Ingredient milk = ingredient(1L, "Sữa tươi", "hộp", "0", "25000");
        InventoryBatch batch = batch(101L, milk, TODAY.plusDays(1), "5.000", "25000.00");

        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                eq(STORE_ID), eq(BigDecimal.ZERO), eq(TODAY.plusDays(3))
        )).thenReturn(List.of(batch));
        when(batchRepository.sumSellableGroupedByIngredientIds(any(), any(), any()))
                .thenReturn(List.of(stockRow(1L, "5")));

        // Đã có action OPEN trước đó
        DailyAction existing = new DailyAction();
        existing.setId(999L);
        existing.setTenantId(STORE_ID);
        existing.setActionType(DailyActionType.EXPIRY_RISK);
        existing.setStatus(DailyActionStatus.OPEN);
        existing.setProduct(milk);
        existing.setBatch(batch);
        when(dailyActionRepository.findOpenByRecomputeKey(
                eq(STORE_ID), eq(DailyActionType.EXPIRY_RISK), eq(1L), eq(101L)
        )).thenReturn(Optional.of(existing));

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(List.of(milk));
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                anyLong(), anyList(), any(), anyString(), any()
        )).thenReturn(Collections.emptyList());

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.expiryRiskCreated()).isEqualTo(0);
        assertThat(result.expiryRiskUpdated()).isEqualTo(1);
    }

    @Test
    void noExpiryRiskActionsWhenNoBatchesInWindow() {
        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                eq(STORE_ID), eq(BigDecimal.ZERO), eq(TODAY.plusDays(3))
        )).thenReturn(Collections.emptyList());

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(Collections.emptyList());

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.expiryRiskCreated()).isEqualTo(0);
        assertThat(result.expiryRiskUpdated()).isEqualTo(0);
    }

    // ─── REORDER ──────────────────────────────────────────────────────────────

    @Test
    void createsReorderActionWhenStockBelowMinStock() {
        Ingredient sugar = ingredient(2L, "Đường", "kg", "10", "5000");
        // Tồn kho 5 kg, minStock 10 kg → REORDER
        InventoryBatchRepository.IngredientQuantity stock = stockRow(2L, "5");

        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                any(), any(), any()
        )).thenReturn(Collections.emptyList()); // Không có EXPIRY_RISK

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(List.of(sugar));
        when(batchRepository.sumSellableGroupedByIngredientIds(
                eq(STORE_ID), anyList(), eq(TODAY)
        )).thenReturn(List.of(stock));
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID), anyList(), any(StockTransactionType.class), anyString(), any()
        )).thenReturn(List.of(txRow(2L, "7"))); // dùng 7kg trong 14 ngày = 0.5/ngày

        when(dailyActionRepository.findOpenByRecomputeKey(
                eq(STORE_ID), eq(DailyActionType.REORDER), eq(2L), isNull()
        )).thenReturn(Optional.empty());

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.reorderCreated()).isEqualTo(1);
        assertThat(result.reorderUpdated()).isEqualTo(0);

        var captor = org.mockito.ArgumentCaptor.forClass(DailyAction.class);
        verify(dailyActionRepository, atLeastOnce()).save(captor.capture());

        DailyAction saved = captor.getAllValues().stream()
                .filter(a -> a.getActionType() == DailyActionType.REORDER)
                .findFirst()
                .orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(DailyActionStatus.OPEN);
        assertThat(saved.getRiskQtyMin()).isPositive();
        assertThat(saved.getTitle()).contains("mức an toàn");
    }

    @Test
    void noReorderActionWhenStockAboveMinStockAndNotNearStockout() {
        Ingredient oil = ingredient(3L, "Dầu ăn", "lít", "2", "15000");

        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(List.of(oil));
        // Tồn 20 lít, minStock 2 lít → không cần REORDER
        when(batchRepository.sumSellableGroupedByIngredientIds(any(), anyList(), any()))
                .thenReturn(List.of(stockRow(3L, "20")));
        // Trung bình tiêu thụ 1 lít/ngày → 20 ngày → không near-stockout
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                anyLong(), anyList(), any(), anyString(), any()
        )).thenReturn(List.of(txRow(3L, "14"))); // 14 lít / 14 ngày = 1/ngày → daysUntilStockout = 20

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.reorderCreated()).isEqualTo(0);
        verify(dailyActionRepository, never()).save(
                argThat(a -> a.getActionType() == DailyActionType.REORDER)
        );
    }

    // ─── Auto-resolve ─────────────────────────────────────────────────────────

    @Test
    void autoResolveIsCalledBeforeComputation() {
        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                any(), any(), any()
        )).thenReturn(Collections.emptyList());
        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(Collections.emptyList());

        service.computeForStore(STORE_ID);

        verify(dailyActionRepository).autoResolveStaleExpiryRisk(eq(STORE_ID), any());
        verify(dailyActionRepository).autoResolveStaleReorder(eq(STORE_ID), eq(TODAY));
    }

    @Test
    void cleanupIsCalledAfterComputation() {
        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                any(), any(), any()
        )).thenReturn(Collections.emptyList());
        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(Collections.emptyList());

        service.computeForStore(STORE_ID);

        verify(dailyActionRepository).deleteStaleClosedActions(eq(STORE_ID), any(Instant.class));
    }

    @Test
    void createsAnomalyActionWhenRecentConsumptionSpikes() {
        Ingredient milk = ingredient(1L, "Sữa tươi", "hộp", "10", "25000");

        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                any(), any(), any()
        )).thenReturn(Collections.emptyList()); // No expiry warning

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(List.of(milk));

        // Tồn kho nhiều để tránh Reorder trigger (20 hộp, minStock = 10)
        when(batchRepository.sumSellableGroupedByIngredientIds(any(), anyList(), any()))
                .thenReturn(List.of(stockRow(1L, "20")));

        // Mock 24h consumption: 5 hộp (lớn hơn ngưỡng min 1.0)
        Instant now = Instant.now(clock);
        Instant last24hStart = now.minus(24, ChronoUnit.HOURS);
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID),
                anyList(),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                eq(last24hStart)
        )).thenReturn(List.of(txRow(1L, "5.0")));

        // Mock lookback consumption: 10 hộp total (5 hộp 24h + 5 hộp lịch sử 28 ngày)
        // average daily baseline = 5 / 28 = 0.178.
        // 5.0 is much higher than 0.178 * 1.25 -> Anomaly!
        Instant historyStart = now.minus(29, ChronoUnit.DAYS);
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID),
                anyList(),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                eq(historyStart)
        )).thenReturn(List.of(txRow(1L, "10.0")));

        when(dailyActionRepository.findOpenByRecomputeKey(
                eq(STORE_ID), eq(DailyActionType.ANOMALY), eq(1L), isNull()
        )).thenReturn(Optional.empty());

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.anomalyCreated()).isEqualTo(1);
        assertThat(result.anomalyUpdated()).isEqualTo(0);

        var captor = org.mockito.ArgumentCaptor.forClass(DailyAction.class);
        verify(dailyActionRepository, atLeastOnce()).save(captor.capture());

        DailyAction saved = captor.getAllValues().stream()
                .filter(a -> a.getActionType() == DailyActionType.ANOMALY)
                .findFirst()
                .orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(DailyActionStatus.OPEN);
        assertThat(saved.getTitle()).contains("Tiêu thụ tăng bất thường");
    }

    @Test
    void noAnomalyActionWhenRecentConsumptionIsNormal() {
        Ingredient milk = ingredient(1L, "Sữa tươi", "hộp", "10", "25000");

        when(batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                any(), any(), any()
        )).thenReturn(Collections.emptyList());

        when(ingredientRepository.findByStoreIdAndDeletedFalse(STORE_ID))
                .thenReturn(List.of(milk));

        when(batchRepository.sumSellableGroupedByIngredientIds(any(), anyList(), any()))
                .thenReturn(List.of(stockRow(1L, "20")));

        // Mock 24h consumption: 1 hộp
        Instant now = Instant.now(clock);
        Instant last24hStart = now.minus(24, ChronoUnit.HOURS);
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID),
                anyList(),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                eq(last24hStart)
        )).thenReturn(List.of(txRow(1L, "1.0")));

        // Mock lookback consumption: 30 hộp total (1 hộp 24h + 29 hộp lịch sử 28 ngày)
        // average daily baseline = 29 / 28 = 1.03
        // 1.0 is not higher than 1.03 * 1.25 -> No anomaly
        Instant historyStart = now.minus(29, ChronoUnit.DAYS);
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID),
                anyList(),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                eq(historyStart)
        )).thenReturn(List.of(txRow(1L, "30.0")));

        DailyActionComputationService.ComputationResult result = service.computeForStore(STORE_ID);

        assertThat(result.anomalyCreated()).isEqualTo(0);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Ingredient ingredient(Long id, String name, String unit, String minStock, String unitCost) {
        Store store = new Store();
        store.setId(STORE_ID);
        Ingredient i = new Ingredient();
        i.setId(id);
        i.setName(name);
        i.setCode("ING-" + id);
        i.setUnit(unit);
        i.setMinStock(new BigDecimal(minStock));
        i.setUnitCost(new BigDecimal(unitCost));
        i.setStore(store);
        return i;
    }

    private InventoryBatch batch(Long id, Ingredient ingredient, LocalDate expiryDate,
                                  String quantity, String costPerUnit) {
        InventoryBatch b = new InventoryBatch();
        b.setId(id);
        b.setIngredient(ingredient);
        b.setStore(ingredient.getStore());
        b.setExpiryDate(expiryDate);
        b.setQuantity(new BigDecimal(quantity));
        b.setCostPerUnit(new BigDecimal(costPerUnit));
        b.setBatchNumber("BATCH-" + id);
        return b;
    }

    private InventoryBatchRepository.IngredientQuantity stockRow(Long ingredientId, String quantity) {
        return new InventoryBatchRepository.IngredientQuantity() {
            @Override public Long getIngredientId() { return ingredientId; }
            @Override public java.math.BigDecimal getQuantity() { return new BigDecimal(quantity); }
        };
    }

    private StockTransactionRepository.IngredientQuantity txRow(Long ingredientId, String quantity) {
        return new StockTransactionRepository.IngredientQuantity() {
            @Override public Long getIngredientId() { return ingredientId; }
            @Override public java.math.BigDecimal getQuantity() { return new BigDecimal(quantity); }
        };
    }
}
