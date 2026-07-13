package vn.inventoryai.insight;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.insight.dto.InventoryInsightResponse;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatch;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransaction;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryInsightService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final int ORDER_COVER_DAYS = 7;
    private static final int MIN_HISTORY_TRANSACTIONS_FOR_WEEKDAY = 14;

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<InventoryInsightResponse> inventoryInsights() {
        Long storeId = SecurityUtils.storeId();
        return inventoryInsights(storeId);
    }

    @Transactional(readOnly = true)
    public List<InventoryInsightResponse> inventoryInsights(Long storeId) {
        LocalDate today = LocalDate.now(clock);
        Instant from28d = today.minusDays(28).atStartOfDay(ZoneId.systemDefault()).toInstant();

        return ingredientRepository.findByStoreIdAndDeletedFalse(storeId).stream()
                .map(ingredient -> buildInsight(storeId, ingredient, today, from28d))
                .sorted(Comparator.comparing(InventoryInsightResponse::wasteRiskLevel).reversed()
                        .thenComparing(InventoryInsightResponse::daysUntilStockout, Comparator.nullsLast(BigDecimal::compareTo)))
                .toList();
    }

    private InventoryInsightResponse buildInsight(Long storeId, Ingredient ingredient, LocalDate today, Instant from28d) {
        List<StockTransaction> usageTransactions = transactionRepository
                .findByStoreIdAndIngredientIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        storeId,
                        ingredient.getId(),
                        StockTransactionType.OUT,
                        from28d
                );
        BigDecimal avg7d = averageDailyUsage(usageTransactions, today.minusDays(7), today);
        BigDecimal avg28d = averageDailyUsage(usageTransactions, today.minusDays(28), today);
        BigDecimal weekdayAdjusted = weekdayAdjustedUsage(usageTransactions, today.getDayOfWeek());
        BigDecimal usageRate = weekdayAdjusted == null ? avg7d.max(avg28d) : weekdayAdjusted;

        BigDecimal currentStock = scale(batchRepository.sumAvailable(storeId, ingredient.getId()));
        List<InventoryBatch> availableBatches = batchRepository
                .findByStoreIdAndIngredientIdAndQuantityGreaterThanOrderByExpiryDateAsc(storeId, ingredient.getId(), BigDecimal.ZERO);
        InventoryBatch nearestBatch = availableBatches.isEmpty() ? null : availableBatches.getFirst();
        Integer daysUntilExpiry = nearestBatch == null
                ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(today, nearestBatch.getExpiryDate()));

        BigDecimal daysUntilStockout = usageRate.signum() == 0
                ? null
                : currentStock.divide(usageRate, 1, RoundingMode.HALF_UP);
        WasteRiskLevel riskLevel = riskLevel(
                currentStock,
                ingredient.getMinStock(),
                usageRate,
                daysUntilStockout,
                daysUntilExpiry,
                usageTransactions.size()
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
                "ING-" + ingredient.getId(),
                ingredient.getUnit(),
                nearestBatch == null ? null : nearestBatch.getId(),
                nearestBatch == null ? null : nearestBatch.getExpiryDate(),
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

    private BigDecimal averageDailyUsage(List<StockTransaction> transactions, LocalDate fromExclusive, LocalDate today) {
        BigDecimal total = transactions.stream()
                .filter(tx -> !LocalDate.ofInstant(tx.getCreatedAt(), ZoneId.systemDefault()).isBefore(fromExclusive))
                .filter(tx -> LocalDate.ofInstant(tx.getCreatedAt(), ZoneId.systemDefault()).isBefore(today.plusDays(1)))
                .map(StockTransaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long days = ChronoUnit.DAYS.between(fromExclusive, today);
        return days <= 0 ? ZERO : scale(total.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP));
    }

    private BigDecimal weekdayAdjustedUsage(List<StockTransaction> transactions, DayOfWeek dayOfWeek) {
        if (transactions.size() < MIN_HISTORY_TRANSACTIONS_FOR_WEEKDAY) {
            return null;
        }
        List<StockTransaction> sameWeekday = transactions.stream()
                .filter(tx -> LocalDate.ofInstant(tx.getCreatedAt(), ZoneId.systemDefault()).getDayOfWeek() == dayOfWeek)
                .toList();
        if (sameWeekday.size() < 2) {
            return null;
        }
        BigDecimal total = sameWeekday.stream()
                .map(StockTransaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return scale(total.divide(BigDecimal.valueOf(sameWeekday.size()), 3, RoundingMode.HALF_UP));
    }

    private WasteRiskLevel riskLevel(
            BigDecimal currentStock,
            BigDecimal minStock,
            BigDecimal usageRate,
            BigDecimal daysUntilStockout,
            Integer daysUntilExpiry,
            int historyTransactionCount
    ) {
        boolean reliableForOverstock = historyTransactionCount >= MIN_HISTORY_TRANSACTIONS_FOR_WEEKDAY;
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
            InventoryBatch nearestBatch,
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
            bullets.add("Chưa có tiêu thụ OUT trong 28 ngày nên chưa ước tính ngày cạn kho.");
        } else {
            bullets.add("Ước tính còn " + fmt(daysUntilStockout) + " ngày trước khi cạn kho.");
        }
        if (nearestBatch != null && daysUntilExpiry != null) {
            bullets.add("Lô gần hạn nhất BATCH-" + nearestBatch.getId() + " còn " + daysUntilExpiry + " ngày, tồn " + fmt(nearestBatch.getQuantity()) + " " + ingredient.getUnit() + ".");
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
            return riskLevel == WasteRiskLevel.HIGH ? "Nhập gấp" : "Tạo đơn nhập";
        }
        if (daysUntilExpiry != null && daysUntilExpiry <= 7) {
            return "Ưu tiên dùng trước";
        }
        return "Theo dõi";
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private String fmt(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
