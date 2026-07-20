package vn.inventoryai.daily;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatch;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.common.enums.StockTransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service tính toán và lưu trữ Daily Actions vào DB.
 *
 * Mỗi lần chạy sẽ:
 * 1. Tự động giải quyết các hành động cũ không còn hợp lệ (auto-resolve).
 * 2. Tính toán hành động mới cho EXPIRY_RISK và REORDER.
 * 3. Upsert vào bảng daily_actions (tạo mới hoặc cập nhật nếu đã tồn tại và còn OPEN).
 * 4. Xóa bản ghi RESOLVED/DISMISSED cũ hơn 30 ngày.
 *
 * Chạy trong transaction REQUIRES_NEW để lỗi của một store không ảnh hưởng store khác.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyActionComputationService {

    /**
     * Ngưỡng daysUntilStockout để tạo hành động REORDER (đơn vị: ngày).
     */
    private static final int REORDER_THRESHOLD_DAYS = 7;

    /**
     * Bảng ghi RESOLVED/DISMISSED cũ hơn 30 ngày sẽ bị xóa.
     */
    private static final int CLEANUP_DAYS = 30;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DailyActionRepository dailyActionRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    /**
     * Kết quả tính toán cho một store.
     */
    public record ComputationResult(
            int expiryRiskCreated,
            int expiryRiskUpdated,
            int reorderCreated,
            int reorderUpdated,
            int anomalyCreated,
            int anomalyUpdated,
            int autoResolved,
            int cleanedUp
    ) {
    }

    /**
     * Tính toán tất cả Daily Actions cho một store.
     * Chạy trong transaction REQUIRES_NEW để tách biệt hoàn toàn với store khác.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ComputationResult computeForStore(Long storeId) {
        LocalDate today = LocalDate.now(clock);
        TenantSettings settings = tenantSettingsRepository.findByTenantId(storeId)
                .orElseGet(() -> defaultSettings(storeId));

        LocalDate expiryThreshold = today.plusDays(settings.getExpiryWarningDays());

        // Bước 1: Tự động giải quyết hành động không còn hợp lệ hoặc quá hạn
        int resolvedExpired = dailyActionRepository.resolveExpiredActions(storeId, Instant.now(clock));
        int resolved1 = dailyActionRepository.autoResolveStaleExpiryRisk(storeId, expiryThreshold);
        int resolved2 = dailyActionRepository.autoResolveStaleReorder(storeId, today);
        int autoResolved = resolvedExpired + resolved1 + resolved2;

        // Bước 2: Tính toán EXPIRY_RISK
        ExpiryRiskCount expiryCount = computeExpiryRisk(storeId, today, expiryThreshold, settings);

        // Bước 3: Tính toán REORDER
        ReorderCount reorderCount = computeReorder(storeId, today, settings);

        // Bước 3.5: Tính toán ANOMALY
        AnomalyCount anomalyCount = computeAnomaly(storeId, today, settings);

        // Bước 4: Dọn dẹp bản ghi cũ
        Instant cleanupThreshold = Instant.now(clock).minus(CLEANUP_DAYS, ChronoUnit.DAYS);
        int cleanedUp = dailyActionRepository.deleteStaleClosedActions(storeId, cleanupThreshold);

        log.debug("Daily actions computed for storeId={}: expiryRisk={}/{}, reorder={}/{}, anomaly={}/{}, autoResolved={}, cleanedUp={}",
                storeId,
                expiryCount.created(), expiryCount.updated(),
                reorderCount.created(), reorderCount.updated(),
                anomalyCount.created(), anomalyCount.updated(),
                autoResolved, cleanedUp);

        return new ComputationResult(
                expiryCount.created(), expiryCount.updated(),
                reorderCount.created(), reorderCount.updated(),
                anomalyCount.created(), anomalyCount.updated(),
                autoResolved, cleanedUp
        );
    }

    // ─── EXPIRY_RISK ──────────────────────────────────────────────────────────

    private record ExpiryRiskCount(int created, int updated) {}

    private ExpiryRiskCount computeExpiryRisk(
            Long storeId,
            LocalDate today,
            LocalDate expiryThreshold,
            TenantSettings settings
    ) {
        // Tất cả lô hàng còn hàng và HSD trong ngưỡng cảnh báo
        List<InventoryBatch> atRiskBatches = batchRepository
                .findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
                        storeId,
                        ZERO,
                        expiryThreshold
                );

        if (atRiskBatches.isEmpty()) {
            return new ExpiryRiskCount(0, 0);
        }

        // Tính tổng tồn kho theo nguyên liệu để dùng trong tính risk value
        Set<Long> ingredientIds = atRiskBatches.stream()
                .map(b -> b.getIngredient().getId())
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> stockByIngredient = batchRepository
                .sumSellableGroupedByIngredientIds(storeId, ingredientIds, today)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getIngredientId(),
                        row -> row.getQuantity(),
                        BigDecimal::add
                ));

        int created = 0;
        int updated = 0;

        for (InventoryBatch batch : atRiskBatches) {
            Ingredient product = batch.getIngredient();
            long daysUntilExpiry = ChronoUnit.DAYS.between(today, batch.getExpiryDate());

            String title = buildExpiryTitle(daysUntilExpiry);
            String description = buildExpiryDescription(batch, daysUntilExpiry, product);

            BigDecimal riskValueEstimate = batch.getQuantity()
                    .multiply(batch.getCostPerUnit())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal priorityScore = computeExpiryPriorityScore(daysUntilExpiry, riskValueEstimate);

            // Metadata JSON thủ công để tránh phụ thuộc Jackson ở tầng này
            String metadata = String.format(
                    "{\"daysUntilExpiry\":%d,\"batchNumber\":\"%s\",\"batchQty\":%s}",
                    daysUntilExpiry,
                    batch.getBatchNumber(),
                    batch.getQuantity().toPlainString()
            );

            Optional<DailyAction> existing = dailyActionRepository.findOpenByRecomputeKey(
                    storeId, DailyActionType.EXPIRY_RISK, product.getId(), batch.getId()
            );

            if (existing.isPresent()) {
                DailyAction action = existing.get();
                action.setTitle(title);
                action.setDescription(description);
                action.setRiskValueEstimate(riskValueEstimate);
                action.setPriorityScore(priorityScore);
                action.setMetadata(metadata);
                action.setComputedAt(Instant.now(clock));
                action.setExpiresAt(batch.getExpiryDate().atStartOfDay(ZoneOffset.UTC).toInstant());
                dailyActionRepository.save(action);
                updated++;
            } else {
                DailyAction action = new DailyAction();
                action.setTenantId(storeId);
                action.setActionType(DailyActionType.EXPIRY_RISK);
                action.setProduct(product);
                action.setBatch(batch);
                action.setTitle(title);
                action.setDescription(description);
                action.setRiskQtyMin(batch.getQuantity());
                action.setRiskQtyMax(batch.getQuantity());
                action.setRiskValueEstimate(riskValueEstimate);
                action.setPriorityScore(priorityScore);
                action.setStatus(DailyActionStatus.OPEN);
                action.setComputedAt(Instant.now(clock));
                action.setExpiresAt(batch.getExpiryDate().atStartOfDay(ZoneOffset.UTC).toInstant());
                action.setMetadata(metadata);
                dailyActionRepository.save(action);
                created++;
            }
        }

        return new ExpiryRiskCount(created, updated);
    }

    /**
     * Điểm EXPIRY_RISK: HSD càng gần = điểm càng cao.
     * Công thức: (1000 / max(days, 0.1)) * riskValue.
     * Với batch đã hết hạn (days <= 0), dùng 0.1 để ưu tiên tối đa.
     */
    private BigDecimal computeExpiryPriorityScore(long daysUntilExpiry, BigDecimal riskValue) {
        double effectiveDays = Math.max(daysUntilExpiry, 0.1);
        double urgencyFactor = 1000.0 / effectiveDays;
        BigDecimal urgency = BigDecimal.valueOf(urgencyFactor).setScale(6, RoundingMode.HALF_UP);
        if (riskValue.signum() <= 0) return urgency;
        return urgency.multiply(riskValue.max(BigDecimal.ONE))
                .divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

    private String buildExpiryTitle(long daysUntilExpiry) {
        if (daysUntilExpiry < 0) return "Lô hàng đã hết hạn sử dụng";
        if (daysUntilExpiry == 0) return "Lô hàng hết hạn hôm nay";
        return "Lô hàng hết hạn sau " + daysUntilExpiry + " ngày";
    }

    private String buildExpiryDescription(InventoryBatch batch, long daysUntilExpiry, Ingredient product) {
        return String.format(
                "Lô %s còn %.3f %s (trị giá ước tính %s VND). %s",
                batch.getBatchNumber(),
                batch.getQuantity(),
                product.getUnit(),
                batch.getQuantity().multiply(batch.getCostPerUnit()).toPlainString(),
                daysUntilExpiry <= 0 ? "Cần xử lý ngay để tránh lãng phí." : "Ưu tiên sử dụng lô này trước."
        );
    }

    // ─── REORDER ──────────────────────────────────────────────────────────────

    private record ReorderCount(int created, int updated) {}

    private ReorderCount computeReorder(Long storeId, LocalDate today, TenantSettings settings) {
        List<Ingredient> ingredients = ingredientRepository.findByStoreIdAndDeletedFalse(storeId);
        if (ingredients.isEmpty()) {
            return new ReorderCount(0, 0);
        }

        List<Long> ingredientIds = ingredients.stream().map(Ingredient::getId).toList();

        // Tổng tồn kho còn hạn
        Map<Long, BigDecimal> stockByIngredient = batchRepository
                .sumSellableGroupedByIngredientIds(storeId, ingredientIds, today)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getIngredientId(),
                        row -> row.getQuantity(),
                        BigDecimal::add
                ));

        // Lượng xuất kho trong lookback period để tính avgDailyUsage
        int lookbackDays = settings.getReorderConsumptionLookbackDays();
        Instant fromInstant = today.minusDays(lookbackDays - 1L)
                .atStartOfDay(clock.getZone()).toInstant();

        Map<Long, BigDecimal> usedByIngredient = transactionRepository
                .sumQuantitySinceGroupedByIngredientIds(
                        storeId,
                        ingredientIds,
                        StockTransactionType.OUT,
                        "EXPORT_CONSUME",
                        fromInstant
                )
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getIngredientId(),
                        row -> row.getQuantity(),
                        BigDecimal::add
                ));

        int created = 0;
        int updated = 0;

        for (Ingredient ingredient : ingredients) {
            Long id = ingredient.getId();
            BigDecimal currentStock = stockByIngredient.getOrDefault(id, ZERO);
            BigDecimal consumed = usedByIngredient.getOrDefault(id, ZERO);
            BigDecimal avgDailyUsage = lookbackDays > 0
                    ? consumed.divide(BigDecimal.valueOf(lookbackDays), 3, RoundingMode.HALF_UP)
                    : ZERO;

            // Tính số ngày có thể cạn kho
            BigDecimal daysUntilStockout = avgDailyUsage.signum() > 0
                    ? currentStock.divide(avgDailyUsage, 1, RoundingMode.HALF_UP)
                    : null;

            // Chỉ tạo REORDER khi tồn dưới min_stock HOẶC sắp cạn trong REORDER_THRESHOLD_DAYS
            boolean belowMinStock = currentStock.compareTo(ingredient.getMinStock()) < 0;
            boolean nearStockout = daysUntilStockout != null
                    && daysUntilStockout.compareTo(BigDecimal.valueOf(REORDER_THRESHOLD_DAYS)) <= 0;

            if (!belowMinStock && !nearStockout) {
                continue;
            }

            // Tính lượng cần đặt
            BigDecimal reviewDays = settings.getReorderReviewPeriodDays();
            BigDecimal safetyBuffer = settings.getReorderSafetyBufferDays();
            BigDecimal target = avgDailyUsage.multiply(reviewDays.add(safetyBuffer))
                    .add(ingredient.getMinStock());
            BigDecimal recommendedOrderQty = target.subtract(currentStock).max(ZERO)
                    .setScale(3, RoundingMode.HALF_UP);

            if (recommendedOrderQty.signum() == 0 && !belowMinStock) continue;

            BigDecimal riskValue = recommendedOrderQty.multiply(ingredient.getUnitCost())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal priorityScore = computeReorderPriorityScore(
                    daysUntilStockout, avgDailyUsage, ingredient.getUnitCost()
            );

            String title = buildReorderTitle(belowMinStock, daysUntilStockout);
            String description = buildReorderDescription(
                    ingredient, currentStock, avgDailyUsage, recommendedOrderQty, daysUntilStockout
            );

            String metadata = String.format(
                    "{\"currentStock\":%s,\"avgDailyUsage\":%s,\"recommendedOrderQty\":%s,\"daysUntilStockout\":%s}",
                    currentStock.toPlainString(),
                    avgDailyUsage.toPlainString(),
                    recommendedOrderQty.toPlainString(),
                    daysUntilStockout != null ? daysUntilStockout.toPlainString() : "null"
            );

            Optional<DailyAction> existing = dailyActionRepository.findOpenByRecomputeKey(
                    storeId, DailyActionType.REORDER, id, null
            );

            if (existing.isPresent()) {
                DailyAction action = existing.get();
                action.setTitle(title);
                action.setDescription(description);
                action.setRiskQtyMin(recommendedOrderQty);
                action.setRiskQtyMax(recommendedOrderQty);
                action.setRiskValueEstimate(riskValue);
                action.setPriorityScore(priorityScore);
                action.setMetadata(metadata);
                action.setComputedAt(Instant.now(clock));
                dailyActionRepository.save(action);
                updated++;
            } else {
                DailyAction action = new DailyAction();
                action.setTenantId(storeId);
                action.setActionType(DailyActionType.REORDER);
                action.setProduct(ingredient);
                action.setBatch(null);
                action.setTitle(title);
                action.setDescription(description);
                action.setRiskQtyMin(recommendedOrderQty);
                action.setRiskQtyMax(recommendedOrderQty);
                action.setRiskValueEstimate(riskValue);
                action.setPriorityScore(priorityScore);
                action.setStatus(DailyActionStatus.OPEN);
                action.setComputedAt(Instant.now(clock));
                action.setMetadata(metadata);
                dailyActionRepository.save(action);
                created++;
            }
        }

        return new ReorderCount(created, updated);
    }

    /**
     * Điểm REORDER: Kết hợp mức độ khẩn cấp (thời gian cạn kho) và giá trị kinh tế.
     * Công thức: (200 - daysUntilStockout) * avgDailyUsage * unitCost.
     * Nếu không có avgDailyUsage (hàng chưa bao giờ bán), dùng hệ số cố định.
     */
    private BigDecimal computeReorderPriorityScore(
            BigDecimal daysUntilStockout,
            BigDecimal avgDailyUsage,
            BigDecimal unitCost
    ) {
        double urgencyDays = daysUntilStockout != null
                ? Math.max(0, REORDER_THRESHOLD_DAYS * 2 - daysUntilStockout.doubleValue())
                : (double) REORDER_THRESHOLD_DAYS;

        if (avgDailyUsage.signum() <= 0) {
            return BigDecimal.valueOf(urgencyDays).setScale(6, RoundingMode.HALF_UP);
        }
        double economicValue = avgDailyUsage.doubleValue() * unitCost.doubleValue();
        return BigDecimal.valueOf(urgencyDays * economicValue / 1000.0)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private String buildReorderTitle(boolean belowMinStock, BigDecimal daysUntilStockout) {
        if (belowMinStock && daysUntilStockout != null
                && daysUntilStockout.compareTo(BigDecimal.valueOf(3)) <= 0) {
            return "Tồn kho cực thấp, cần nhập gấp";
        }
        if (belowMinStock) return "Tồn kho dưới mức an toàn";
        return "Có thể cạn kho sau " + (daysUntilStockout != null ? daysUntilStockout.intValue() : REORDER_THRESHOLD_DAYS) + " ngày";
    }

    private String buildReorderDescription(
            Ingredient ingredient,
            BigDecimal currentStock,
            BigDecimal avgDailyUsage,
            BigDecimal recommendedOrderQty,
            BigDecimal daysUntilStockout
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tồn hiện tại %.3f %s (mức an toàn %.3f). ",
                currentStock, ingredient.getUnit(), ingredient.getMinStock()));
        if (avgDailyUsage.signum() > 0) {
            sb.append(String.format("Trung bình tiêu thụ %.3f %s/ngày. ", avgDailyUsage, ingredient.getUnit()));
        }
        if (daysUntilStockout != null) {
            sb.append(String.format("Ước tính cạn sau %.1f ngày. ", daysUntilStockout));
        }
        sb.append(String.format("Đề xuất đặt thêm %.3f %s.", recommendedOrderQty, ingredient.getUnit()));
        return sb.toString();
    }

    // ─── ANOMALY ──────────────────────────────────────────────────────────────

    private record AnomalyCount(int created, int updated) {}

    private AnomalyCount computeAnomaly(Long storeId, LocalDate today, TenantSettings settings) {
        List<Ingredient> ingredients = ingredientRepository.findByStoreIdAndDeletedFalse(storeId);
        if (ingredients.isEmpty()) {
            return new AnomalyCount(0, 0);
        }

        List<Long> ingredientIds = ingredients.stream().map(Ingredient::getId).toList();

        Instant now = Instant.now(clock);
        // Lượng tiêu thụ trong 24 giờ qua (sliding window)
        Instant last24hStart = now.minus(24, ChronoUnit.HOURS);

        // Khoảng lịch sử dùng để đối chiếu (mặc định 28 ngày)
        int lookbackDays = Math.max(7, settings.getExpiryConsumptionLookbackDays());
        Instant historyStart = now.minus(lookbackDays + 1L, ChronoUnit.DAYS);

        Map<Long, BigDecimal> last24hConsumption = transactionRepository
                .sumQuantitySinceGroupedByIngredientIds(
                        storeId,
                        ingredientIds,
                        StockTransactionType.OUT,
                        "EXPORT_CONSUME",
                        last24hStart
                )
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getIngredientId(),
                        row -> row.getQuantity(),
                        BigDecimal::add
                ));

        Map<Long, BigDecimal> lookbackConsumption = transactionRepository
                .sumQuantitySinceGroupedByIngredientIds(
                        storeId,
                        ingredientIds,
                        StockTransactionType.OUT,
                        "EXPORT_CONSUME",
                        historyStart
                )
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getIngredientId(),
                        row -> row.getQuantity(),
                        BigDecimal::add
                ));

        int created = 0;
        int updated = 0;

        BigDecimal thresholdPercent = settings.getAnomalyThresholdPercent();
        BigDecimal minAbsoluteQty = settings.getAnomalyMinAbsoluteQuantity();

        for (Ingredient ingredient : ingredients) {
            Long id = ingredient.getId();
            BigDecimal last24hQty = last24hConsumption.getOrDefault(id, ZERO);

            // Bỏ qua nếu lượng tiêu thụ quá bé để tránh nhiễu hệ thống (ví dụ: dùng thêm 0.05g muối)
            if (last24hQty.compareTo(minAbsoluteQty) < 0) {
                continue;
            }

            BigDecimal totalConsumed = lookbackConsumption.getOrDefault(id, ZERO);
            // Lượng tiêu thụ lịch sử loại trừ 24h qua
            BigDecimal historyQty = totalConsumed.subtract(last24hQty).max(ZERO);
            BigDecimal avgHistoryDaily = historyQty.divide(BigDecimal.valueOf(lookbackDays), 3, RoundingMode.HALF_UP);

            boolean isAnomaly = false;
            BigDecimal diffPercent = ZERO;

            if (avgHistoryDaily.signum() == 0) {
                // Lịch sử không tiêu thụ nhưng đột ngột tiêu thụ số lượng lớn -> Anomaly!
                isAnomaly = true;
                diffPercent = HUNDRED;
            } else {
                BigDecimal ratio = last24hQty.divide(avgHistoryDaily, 4, RoundingMode.HALF_UP);
                BigDecimal thresholdRatio = BigDecimal.ONE.add(thresholdPercent.divide(HUNDRED, 4, RoundingMode.HALF_UP));

                if (ratio.compareTo(thresholdRatio) > 0) {
                    isAnomaly = true;
                    diffPercent = ratio.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                }
            }

            if (!isAnomaly) {
                continue;
            }

            BigDecimal excess = last24hQty.subtract(avgHistoryDaily).max(ZERO);
            BigDecimal riskValue = excess.multiply(ingredient.getUnitCost()).setScale(2, RoundingMode.HALF_UP);

            // Priority score dựa trên giá trị tài chính chênh lệch (đơn vị: nghìn đồng)
            BigDecimal priorityScore = riskValue.compareTo(ZERO) > 0
                    ? riskValue.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    : excess.multiply(BigDecimal.valueOf(10)).setScale(6, RoundingMode.HALF_UP);

            String title = "Tiêu thụ tăng bất thường";
            String description = String.format(
                    "Nguyên liệu tiêu thụ tăng vọt %.3f %s trong 24 giờ qua so với trung bình lịch sử %.3f %s/ngày (tăng %s%%).",
                    last24hQty, ingredient.getUnit(), avgHistoryDaily, ingredient.getUnit(), diffPercent.toPlainString()
            );

            String metadata = String.format(
                    "{\"last24hConsumption\":%s,\"avgHistoryDaily\":%s,\"diffPercent\":%s}",
                    last24hQty.toPlainString(),
                    avgHistoryDaily.toPlainString(),
                    diffPercent.toPlainString()
            );

            Optional<DailyAction> existing = dailyActionRepository.findOpenByRecomputeKey(
                    storeId, DailyActionType.ANOMALY, id, null
            );

            // Anomaly cảnh báo có hạn dùng tự động 3 ngày để không làm loãng bảng
            Instant expiresAt = Instant.now(clock).plus(3, ChronoUnit.DAYS);

            if (existing.isPresent()) {
                DailyAction action = existing.get();
                action.setTitle(title);
                action.setDescription(description);
                action.setRiskQtyMin(excess);
                action.setRiskQtyMax(last24hQty);
                action.setRiskValueEstimate(riskValue);
                action.setPriorityScore(priorityScore);
                action.setMetadata(metadata);
                action.setComputedAt(Instant.now(clock));
                action.setExpiresAt(expiresAt);
                dailyActionRepository.save(action);
                updated++;
            } else {
                DailyAction action = new DailyAction();
                action.setTenantId(storeId);
                action.setActionType(DailyActionType.ANOMALY);
                action.setProduct(ingredient);
                action.setBatch(null);
                action.setTitle(title);
                action.setDescription(description);
                action.setRiskQtyMin(excess);
                action.setRiskQtyMax(last24hQty);
                action.setRiskValueEstimate(riskValue);
                action.setPriorityScore(priorityScore);
                action.setStatus(DailyActionStatus.OPEN);
                action.setComputedAt(Instant.now(clock));
                action.setExpiresAt(expiresAt);
                action.setMetadata(metadata);
                dailyActionRepository.save(action);
                created++;
            }
        }

        return new AnomalyCount(created, updated);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Tạo TenantSettings mặc định (chỉ dùng trong bộ nhớ, không persist).
     * Trong thực tế, bảng tenant_settings đã được insert tự động qua V7 migration
     * cho tất cả store hiện có. Đây là fallback phòng thủ.
     */
    private TenantSettings defaultSettings(Long storeId) {
        TenantSettings s = new TenantSettings();
        s.setTenantId(storeId);
        return s;
    }
}
