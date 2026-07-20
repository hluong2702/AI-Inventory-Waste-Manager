package vn.inventoryai.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.alert.AlertRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.dashboard.dto.DashboardResponse;
import vn.inventoryai.insight.InventoryInsightService;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.WasteRecordRepository;
import vn.inventoryai.subscription.SubscriptionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final String ADVANCED_FORECAST = "ADVANCED_FORECAST";

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final WasteRecordRepository wasteRecordRepository;
    private final AlertRepository alertRepository;
    private final InventoryInsightService insightService;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse current() {
        Long storeId = SecurityUtils.storeId();
        Role role = SecurityUtils.principal().role();
        LocalDate today = LocalDate.now(clock);
        LocalDate periodStart = today.withDayOfMonth(1);
        LocalDate periodEndExclusive = today.plusDays(1);
        boolean reportsAvailable = role == Role.OWNER || role == Role.MANAGER;
        boolean insightsAvailable = subscriptionService.hasFeature(storeId, ADVANCED_FORECAST);

        DashboardResponse.WasteSummary waste = reportsAvailable
                ? wasteSummary(storeId, periodStart, periodEndExclusive)
                : null;
        DashboardResponse.InsightSummary insights = insightsAvailable
                ? insightSummary(storeId)
                : null;

        List<DashboardResponse.NearExpiryBatch> nearExpiryBatches = batchRepository
                .findTop5ByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqualOrderByExpiryDateAscReceivedAtAscIdAsc(
                        storeId,
                        BigDecimal.ZERO,
                        today.plusDays(7)
                )
                .stream()
                .map(batch -> new DashboardResponse.NearExpiryBatch(
                        batch.getId(),
                        batch.getIngredient().getId(),
                        batch.getIngredient().getName(),
                        batch.getIngredient().getUnit(),
                        batch.getBatchNumber(),
                        zero(batch.getQuantity()),
                        batch.getExpiryDate(),
                        ChronoUnit.DAYS.between(today, batch.getExpiryDate()),
                        zero(batch.getCostPerUnit())
                ))
                .toList();

        List<DashboardResponse.OpenAlert> alerts = alertRepository
                .findTop3ByStoreIdAndResolvedFalseOrderByCreatedAtDesc(storeId)
                .stream()
                .map(alert -> new DashboardResponse.OpenAlert(
                        alert.getId(),
                        alert.getType(),
                        alert.getIngredient().getId(),
                        alert.getIngredient().getName(),
                        alert.getType() + " - " + alert.getIngredient().getName(),
                        alert.getCreatedAt()
                ))
                .toList();

        return new DashboardResponse(
                periodStart,
                today,
                ingredientRepository.countByStoreIdAndDeletedFalse(storeId),
                alertRepository.countByStoreIdAndResolvedFalse(storeId),
                reportsAvailable,
                role == Role.OWNER || role == Role.MANAGER,
                reportsAvailable,
                waste,
                insightsAvailable,
                insights,
                nearExpiryBatches,
                alerts
        );
    }

    private DashboardResponse.WasteSummary wasteSummary(
            Long storeId,
            LocalDate currentStart,
            LocalDate currentEndExclusive
    ) {
        LocalDate previousStart = currentStart.minusMonths(1);
        long currentPeriodDays = ChronoUnit.DAYS.between(currentStart, currentEndExclusive);
        LocalDate previousEndExclusive = previousStart.plusDays(currentPeriodDays);
        if (previousEndExclusive.isAfter(currentStart)) {
            previousEndExclusive = currentStart;
        }
        Instant currentStartInstant = atStartOfDay(currentStart);
        Instant currentEndInstant = atStartOfDay(currentEndExclusive);

        BigDecimal currentCost = totalCost(wasteRecordRepository.aggregateTotals(
                storeId,
                currentStartInstant,
                currentEndInstant
        ));
        BigDecimal previousCost = totalCost(wasteRecordRepository.aggregateTotals(
                storeId,
                atStartOfDay(previousStart),
                atStartOfDay(previousEndExclusive)
        ));
        BigDecimal changePercent = previousCost.signum() == 0
                ? BigDecimal.ZERO
                : currentCost.subtract(previousCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousCost, 2, RoundingMode.HALF_UP);

        List<DashboardResponse.TopWasteIngredient> top = wasteRecordRepository.aggregateByIngredient(
                        storeId,
                        currentStartInstant,
                        currentEndInstant,
                        PageRequest.of(0, 5)
                )
                .stream()
                .map(row -> new DashboardResponse.TopWasteIngredient(
                        row.getIngredientId(),
                        row.getIngredientName(),
                        row.getUnit(),
                        zero(row.getQuantity()),
                        zero(row.getEstimatedCost())
                ))
                .toList();
        List<DashboardResponse.DailyWastePoint> daily = wasteRecordRepository.aggregateDaily(
                        storeId,
                        currentStartInstant,
                        currentEndInstant,
                        businessOffset()
                )
                .stream()
                .map(row -> new DashboardResponse.DailyWastePoint(
                        row.getBusinessDate(),
                        zero(row.getEstimatedCost())
                ))
                .toList();
        return new DashboardResponse.WasteSummary(currentCost, previousCost, changePercent, top, daily);
    }

    private DashboardResponse.InsightSummary insightSummary(Long storeId) {
        InventoryInsightService.DashboardInsightSummary summary = insightService.dashboardSummary(storeId, 4);
        return new DashboardResponse.InsightSummary(
                summary.totalCount(),
                summary.highRiskCount(),
                summary.suggestedOrderCount(),
                summary.healthyCount(),
                summary.topInsights()
        );
    }

    private BigDecimal totalCost(WasteRecordRepository.WasteTotals totals) {
        return totals == null ? BigDecimal.ZERO : zero(totals.getTotalCost());
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Instant atStartOfDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    private String businessOffset() {
        ZoneOffset offset = clock.getZone().getRules().getOffset(clock.instant());
        return ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
    }
}
