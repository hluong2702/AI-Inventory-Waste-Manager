package vn.inventoryai.insight;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.insight.dto.InventoryInsightResponse;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryInsightService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final int ORDER_COVER_DAYS = 7;
    private static final int MIN_HISTORY_DAYS_FOR_WEEKDAY = 14;
    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 100;
    private static final Comparator<InventoryInsightResponse> DASHBOARD_ORDER = Comparator
            .comparing(InventoryInsightResponse::wasteRiskLevel)
            .reversed()
            .thenComparing(
                    InventoryInsightResponse::daysUntilStockout,
                    Comparator.nullsLast(BigDecimal::compareTo)
            )
            .thenComparing(InventoryInsightResponse::ingredientId);

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<InventoryInsightResponse> inventoryInsights(Pageable pageable) {
        Long storeId = SecurityUtils.storeId();
        return inventoryInsights(storeId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<InventoryInsightResponse> inventoryInsights(Long storeId, Pageable requestedPageable) {
        LocalDate today = LocalDate.now(clock);
        Instant from7d = today.minusDays(6).atStartOfDay(clock.getZone()).toInstant();
        Instant from28d = today.minusDays(27).atStartOfDay(clock.getZone()).toInstant();
        Page<Ingredient> ingredientPage = ingredientRepository.findByStoreIdAndDeletedFalse(
                storeId,
                boundedPageable(requestedPageable)
        );
        if (ingredientPage.isEmpty()) {
            return ingredientPage.map(ingredient -> buildInsight(
                    storeId,
                    ingredient,
                    today,
                    UsageMetrics.empty(),
                    ZERO,
                    null
            ));
        }

        Collection<Long> ingredientIds = ingredientPage.getContent().stream()
                .map(Ingredient::getId)
                .toList();
        Map<Long, UsageMetrics> usageByIngredient = transactionRepository.aggregateConsumptionMetrics(
                        storeId,
                        ingredientIds,
                        from7d,
                        from28d,
                        mysqlDayOfWeek(today),
                        businessOffset()
                ).stream()
                .collect(Collectors.toMap(
                        StockTransactionRepository.IngredientUsageMetrics::getIngredientId,
                        UsageMetrics::from,
                        (left, right) -> left
                ));
        Map<Long, BigDecimal> stockByIngredient = batchRepository
                .sumSellableGroupedByIngredientIds(storeId, ingredientIds, today)
                .stream()
                .collect(Collectors.toMap(
                        InventoryBatchRepository.IngredientQuantity::getIngredientId,
                        InventoryBatchRepository.IngredientQuantity::getQuantity,
                        BigDecimal::add
                ));
        Map<Long, NearestBatch> nearestBatchByIngredient = batchRepository
                .findNearestSellableBatchByIngredientIds(storeId, ingredientIds, today)
                .stream()
                .collect(Collectors.toMap(
                        InventoryBatchRepository.NearestSellableBatch::getIngredientId,
                        NearestBatch::from,
                        (left, right) -> left
                ));

        List<InventoryInsightResponse> content = ingredientPage.getContent().stream()
                .map(ingredient -> buildInsight(
                        storeId,
                        ingredient,
                        today,
                        usageByIngredient.getOrDefault(ingredient.getId(), UsageMetrics.empty()),
                        stockByIngredient.getOrDefault(ingredient.getId(), ZERO),
                        nearestBatchByIngredient.get(ingredient.getId())
                ))
                .sorted(DASHBOARD_ORDER)
                .toList();
        return new PageImpl<>(content, ingredientPage.getPageable(), ingredientPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DashboardInsightSummary dashboardSummary(Long storeId, int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 20);
        int pageNumber = 0;
        long highRiskCount = 0;
        long suggestedOrderCount = 0;
        long healthyCount = 0;
        long totalCount = 0;
        List<InventoryInsightResponse> topInsights = new ArrayList<>(limit);

        while (true) {
            Page<InventoryInsightResponse> page = inventoryInsights(
                    storeId,
                    PageRequest.of(pageNumber, MAX_PAGE_SIZE)
            );
            for (InventoryInsightResponse insight : page.getContent()) {
                totalCount++;
                if (insight.wasteRiskLevel() == WasteRiskLevel.HIGH) highRiskCount++;
                if (insight.wasteRiskLevel() == WasteRiskLevel.LOW) healthyCount++;
                if (insight.recommendedOrderQty().signum() > 0) suggestedOrderCount++;
                topInsights.add(insight);
            }
            topInsights.sort(DASHBOARD_ORDER);
            if (topInsights.size() > limit) {
                topInsights = new ArrayList<>(topInsights.subList(0, limit));
            }
            if (page.isLast()) break;
            pageNumber++;
        }

        return new DashboardInsightSummary(
                totalCount,
                highRiskCount,
                suggestedOrderCount,
                healthyCount,
                List.copyOf(topInsights)
        );
    }

    private InventoryInsightResponse buildInsight(
            Long storeId,
            Ingredient ingredient,
            LocalDate today,
            UsageMetrics usage,
            BigDecimal currentStockValue,
            NearestBatch nearestBatch
    ) {
        BigDecimal avg7d = averageDailyUsage(usage.quantity7d(), 7);
        BigDecimal avg28d = averageDailyUsage(usage.quantity28d(), 28);
        BigDecimal weekdayAdjusted = weekdayAdjustedUsage(usage);
        BigDecimal usageRate = weekdayAdjusted == null ? avg7d.max(avg28d) : weekdayAdjusted;

        BigDecimal currentStock = scale(currentStockValue);
        Integer daysUntilExpiry = nearestBatch == null
                ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(today, nearestBatch.expiryDate()));

        BigDecimal daysUntilStockout = usageRate.signum() == 0
                ? null
                : currentStock.divide(usageRate, 1, RoundingMode.HALF_UP);
        WasteRiskLevel riskLevel = riskLevel(
                currentStock,
                ingredient.getMinStock(),
                usageRate,
                daysUntilStockout,
                daysUntilExpiry,
                usage.historyDayCount()
        );
        BigDecimal recommendedOrderQty = recommendedOrderQty(currentStock, ingredient.getMinStock(), usageRate);
        List<String> bullets = explanationBullets(
                ingredient,
                avg7d,
                avg28d,
                weekdayAdjusted,
                currentStock,
                daysUntilStockout,
                daysUntilExpiry,
                nearestBatch,
                recommendedOrderQty
        );

        return new InventoryInsightResponse(
                storeId,
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getCode(),
                ingredient.getUnit(),
                nearestBatch == null ? null : nearestBatch.batchId(),
                nearestBatch == null ? null : nearestBatch.expiryDate(),
                avg7d,
                avg28d,
                weekdayAdjusted,
                currentStock,
                daysUntilStockout,
                daysUntilExpiry,
                riskLevel,
                recommendedOrderQty,
                bullets,
                ctaLabel(riskLevel, recommendedOrderQty, daysUntilExpiry)
        );
    }

    private BigDecimal averageDailyUsage(BigDecimal total, int days) {
        return scale(safe(total).divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP));
    }

    private BigDecimal weekdayAdjustedUsage(UsageMetrics usage) {
        if (usage.historyDayCount() < MIN_HISTORY_DAYS_FOR_WEEKDAY || usage.weekdayDayCount() < 2) {
            return null;
        }
        return scale(usage.weekdayQuantity().divide(
                BigDecimal.valueOf(usage.weekdayDayCount()),
                3,
                RoundingMode.HALF_UP
        ));
    }

    private WasteRiskLevel riskLevel(
            BigDecimal currentStock,
            BigDecimal minStock,
            BigDecimal usageRate,
            BigDecimal daysUntilStockout,
            Integer daysUntilExpiry,
            int historyDayCount
    ) {
        boolean reliableForOverstock = historyDayCount >= MIN_HISTORY_DAYS_FOR_WEEKDAY;
        BigDecimal target28d = usageRate.multiply(BigDecimal.valueOf(28)).add(minStock);
        if ((daysUntilStockout != null && daysUntilStockout.compareTo(BigDecimal.valueOf(2)) <= 0)
                || (daysUntilExpiry != null && daysUntilExpiry <= 3)
                || (reliableForOverstock && usageRate.signum() > 0 && currentStock.compareTo(target28d) > 0)) {
            return WasteRiskLevel.HIGH;
        }
        BigDecimal target14d = usageRate.multiply(BigDecimal.valueOf(14)).add(minStock);
        if ((daysUntilStockout != null && daysUntilStockout.compareTo(BigDecimal.valueOf(7)) <= 0)
                || currentStock.compareTo(minStock) < 0
                || (daysUntilExpiry != null && daysUntilExpiry <= 7)
                || (reliableForOverstock && usageRate.signum() > 0 && currentStock.compareTo(target14d) > 0)) {
            return WasteRiskLevel.MEDIUM;
        }
        return WasteRiskLevel.LOW;
    }

    private BigDecimal recommendedOrderQty(BigDecimal currentStock, BigDecimal minStock, BigDecimal usageRate) {
        BigDecimal target = usageRate.multiply(BigDecimal.valueOf(ORDER_COVER_DAYS)).add(minStock);
        return scale(target.subtract(currentStock).max(BigDecimal.ZERO));
    }

    private List<String> explanationBullets(
            Ingredient ingredient,
            BigDecimal avg7d,
            BigDecimal avg28d,
            BigDecimal weekdayAdjusted,
            BigDecimal currentStock,
            BigDecimal daysUntilStockout,
            Integer daysUntilExpiry,
            NearestBatch nearestBatch,
            BigDecimal recommendedOrderQty
    ) {
        List<String> bullets = new ArrayList<>();
        bullets.add("7 ngày gần nhất dùng TB " + fmt(avg7d) + " " + ingredient.getUnit() + "/ngày; 28 ngày là " + fmt(avg28d) + ".");
        if (weekdayAdjusted == null) {
            bullets.add("Chưa đủ lịch sử để điều chỉnh theo thứ trong tuần.");
        } else {
            bullets.add("Hôm nay cùng thứ với mẫu lịch sử: mức dùng điều chỉnh " + fmt(weekdayAdjusted) + " " + ingredient.getUnit() + "/ngày.");
        }
        bullets.add("Tồn hiện tại " + fmt(currentStock) + " " + ingredient.getUnit() + "; mức an toàn " + fmt(ingredient.getMinStock()) + ".");
        if (daysUntilStockout == null) {
            bullets.add("Chưa có tiêu thụ sử dụng trong 28 ngày nên chưa ước tính ngày cạn kho.");
        } else {
            bullets.add("Ước tính còn " + fmt(daysUntilStockout) + " ngày trước khi cạn kho.");
        }
        if (nearestBatch != null && daysUntilExpiry != null) {
            bullets.add("Lô gần hạn nhất BATCH-" + nearestBatch.batchId() + " còn " + daysUntilExpiry + " ngày, tồn " + fmt(nearestBatch.quantity()) + " " + ingredient.getUnit() + ".");
        }
        bullets.add(recommendedOrderQty.signum() > 0
                ? "Nên đặt thêm " + fmt(recommendedOrderQty) + " " + ingredient.getUnit() + " để đủ 7 ngày bán + tồn an toàn."
                : "Chưa cần nhập thêm theo mục tiêu 7 ngày bán + tồn an toàn.");
        return bullets;
    }

    private String ctaLabel(WasteRiskLevel riskLevel, BigDecimal recommendedOrderQty, Integer daysUntilExpiry) {
        if (daysUntilExpiry != null && daysUntilExpiry <= 3) {
            return "Ưu tiên dùng trước";
        }
        if (recommendedOrderQty.signum() > 0) {
            return riskLevel == WasteRiskLevel.HIGH ? "Xem nhu cầu nhập gấp" : "Xem nhu cầu nhập";
        }
        if (daysUntilExpiry != null && daysUntilExpiry <= 7) {
            return "Ưu tiên dùng trước";
        }
        return "Theo dõi";
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int mysqlDayOfWeek(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7 + 1;
    }

    private String businessOffset() {
        ZoneOffset offset = clock.getZone().getRules().getOffset(clock.instant());
        return ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
    }

    private Pageable boundedPageable(Pageable requested) {
        int page = requested == null || requested.isUnpaged()
                ? 0
                : Math.max(requested.getPageNumber(), 0);
        int size = requested == null || requested.isUnpaged()
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(requested.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        return PageRequest.of(page, size, sort);
    }

    private String fmt(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private record UsageMetrics(
            BigDecimal quantity7d,
            BigDecimal quantity28d,
            int historyDayCount,
            BigDecimal weekdayQuantity,
            int weekdayDayCount
    ) {
        private static UsageMetrics empty() {
            return new UsageMetrics(BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0);
        }

        private static UsageMetrics from(StockTransactionRepository.IngredientUsageMetrics row) {
            return new UsageMetrics(
                    row.getQuantity7d() == null ? BigDecimal.ZERO : row.getQuantity7d(),
                    row.getQuantity28d() == null ? BigDecimal.ZERO : row.getQuantity28d(),
                    row.getHistoryDayCount() == null ? 0 : Math.toIntExact(row.getHistoryDayCount()),
                    row.getWeekdayQuantity() == null ? BigDecimal.ZERO : row.getWeekdayQuantity(),
                    row.getWeekdayDayCount() == null ? 0 : Math.toIntExact(row.getWeekdayDayCount())
            );
        }
    }

    private record NearestBatch(Long batchId, LocalDate expiryDate, BigDecimal quantity) {
        private static NearestBatch from(InventoryBatchRepository.NearestSellableBatch row) {
            return new NearestBatch(row.getBatchId(), row.getExpiryDate(), row.getQuantity());
        }
    }

    public record DashboardInsightSummary(
            long totalCount,
            long highRiskCount,
            long suggestedOrderCount,
            long healthyCount,
            List<InventoryInsightResponse> topInsights
    ) {
    }
}
