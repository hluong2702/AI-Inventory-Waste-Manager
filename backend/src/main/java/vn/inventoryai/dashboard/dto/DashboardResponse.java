package vn.inventoryai.dashboard.dto;

import vn.inventoryai.common.enums.AlertType;
import vn.inventoryai.insight.dto.InventoryInsightResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        long ingredientCount,
        long openAlertCount,
        boolean canResolveAlerts,
        boolean canViewForecast,
        boolean reportsAvailable,
        WasteSummary waste,
        boolean insightsAvailable,
        InsightSummary insights,
        List<NearExpiryBatch> nearExpiryBatches,
        List<OpenAlert> openAlerts
) {
    public record WasteSummary(
            BigDecimal currentWasteCost,
            BigDecimal previousWasteCost,
            BigDecimal changePercent,
            List<TopWasteIngredient> topWasteIngredients,
            List<DailyWastePoint> dailyCosts
    ) {
    }

    public record TopWasteIngredient(
            Long ingredientId,
            String ingredientName,
            String unit,
            BigDecimal quantity,
            BigDecimal estimatedCost
    ) {
    }

    public record DailyWastePoint(LocalDate date, BigDecimal estimatedCost) {
    }

    public record InsightSummary(
            long totalCount,
            long highRiskCount,
            long suggestedOrderCount,
            long healthyCount,
            List<InventoryInsightResponse> topInsights
    ) {
    }

    public record NearExpiryBatch(
            Long id,
            Long ingredientId,
            String ingredientName,
            String ingredientUnit,
            String batchNumber,
            BigDecimal quantity,
            LocalDate expiryDate,
            long daysUntilExpiry,
            BigDecimal costPerUnit
    ) {
    }

    public record OpenAlert(
            Long id,
            AlertType type,
            Long ingredientId,
            String ingredientName,
            String message,
            Instant createdAt
    ) {
    }
}
