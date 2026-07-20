package vn.inventoryai.forecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.forecast.dto.AiForecastResponse;
import vn.inventoryai.forecast.dto.DailyForecastPoint;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransaction;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service điều phối dự báo nhu cầu AI (Prophet + Weather).
 *
 * Luồng xử lý:
 * 1. Gom lịch sử tiêu thụ từ DB (90 ngày)
 * 2. Lấy tọa độ cửa hàng từ stores.latitude/longitude
 * 3. Gọi Python Prophet microservice qua ProphetForecastClient
 * 4. Nếu Python không khả dụng → Java Moving Average fallback
 * 5. Tính toán aiRecommendedOrder và trả AiForecastResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiForecastService {

    private static final int MIN_HISTORY_DAYS_FOR_PROPHET = 30;

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final StoreRepository storeRepository;
    private final ProphetForecastClient prophetClient;
    private final Clock clock;

    @Value("${app.forecast.history-days:90}")
    private int historyDays;

    @Transactional(readOnly = true)
    public AiForecastResponse forecastWithAi(Long ingredientId, int days) {
        Long storeId = SecurityUtils.storeId();
        int forecastDays = Math.min(Math.max(days, 1), 30);

        // ── 1. Tải nguyên liệu ─────────────────────────────────────────────
        Ingredient ingredient = ingredientRepository
                .findByIdAndStoreIdAndDeletedFalse(ingredientId, storeId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Ingredient not found in current store"
                ));

        // ── 2. Tồn kho hiện tại ───────────────────────────────────────────
        LocalDate today = LocalDate.now(clock);
        BigDecimal currentStock = batchRepository.sumSellable(storeId, ingredientId, today);

        // ── 3. Tọa độ cửa hàng ───────────────────────────────────────────
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"
                ));
        double latitude = store.getLatitude().doubleValue();
        double longitude = store.getLongitude().doubleValue();

        // ── 4. Lịch sử tiêu thụ theo ngày (90 ngày) ──────────────────────
        Instant fromInstant = today.minusDays(historyDays - 1L)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        List<StockTransaction> rawTransactions = transactionRepository
                .findByStoreIdAndIngredientIdAndTypeAndReasonAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        storeId, ingredientId,
                        StockTransactionType.OUT, "EXPORT_CONSUME",
                        fromInstant
                );

        // Gom theo ngày (aggregation in memory — tối đa 90 điểm, nhẹ)
        Map<LocalDate, Double> dailyConsumption = rawTransactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().atZone(clock.getZone()).toLocalDate(),
                        Collectors.summingDouble(tx -> tx.getQuantity().doubleValue())
                ));

        // Điền các ngày thiếu (không có giao dịch) bằng 0 để Prophet hiểu
        List<ProphetForecastClient.ConsumptionPoint> history = new ArrayList<>();
        for (int i = historyDays - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            history.add(new ProphetForecastClient.ConsumptionPoint(
                    d, dailyConsumption.getOrDefault(d, 0.0)
            ));
        }

        // ── 5. Gọi Python Prophet service ────────────────────────────────
        Optional<ProphetForecastClient.ProphetResult> prophetResult = prophetClient.forecast(
                ingredientId, storeId, history, forecastDays, latitude, longitude
        );

        if (prophetResult.isPresent()) {
            ProphetForecastClient.ProphetResult r = prophetResult.get();
            double aiRecommendedOrder = Math.max(0.0,
                    r.totalPredictedDemand()
                    + ingredient.getMinStock().doubleValue()
                    - currentStock.doubleValue()
            );
            double avgDaily = r.dailyBreakdown().isEmpty()
                    ? 0.0
                    : r.totalPredictedDemand() / r.dailyBreakdown().size();

            return new AiForecastResponse(
                    storeId,
                    ingredientId,
                    ingredient.getName(),
                    ingredient.getCode(),
                    ingredient.getUnit(),
                    round(r.totalPredictedDemand()),
                    round(avgDaily),
                    currentStock.doubleValue(),
                    ingredient.getMinStock().doubleValue(),
                    round(aiRecommendedOrder),
                    r.dailyBreakdown(),
                    r.modelUsed(),
                    r.historyDaysUsed(),
                    r.modelAccuracyMape(),
                    r.confidenceNote(),
                    false
            );
        }

        // ── 6. Java fallback (Moving Average) khi Python không khả dụng ──
        log.info("Prophet service unavailable, using Java MA fallback for ingredient_id={}", ingredientId);
        return javaMovingAverageFallback(
                storeId, ingredient, history, forecastDays, today, currentStock
        );
    }

    // ─── Java Moving Average Fallback ─────────────────────────────────────────

    private AiForecastResponse javaMovingAverageFallback(
            Long storeId,
            Ingredient ingredient,
            List<ProphetForecastClient.ConsumptionPoint> history,
            int forecastDays,
            LocalDate today,
            BigDecimal currentStock
    ) {
        // Lấy 14 ngày gần nhất để tính avgDaily
        List<Double> recentValues = history.stream()
                .sorted(Comparator.comparing(ProphetForecastClient.ConsumptionPoint::date).reversed())
                .limit(14)
                .map(ProphetForecastClient.ConsumptionPoint::quantity)
                .toList();

        double avgDaily = recentValues.isEmpty()
                ? 0.0
                : recentValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        List<DailyForecastPoint> breakdown = new ArrayList<>();
        double total = 0.0;

        for (int i = 1; i <= forecastDays; i++) {
            LocalDate forecastDate = today.plusDays(i);
            boolean isWeekend = forecastDate.getDayOfWeek().getValue() >= 6;
            breakdown.add(new DailyForecastPoint(
                    forecastDate,
                    round(avgDaily), round(avgDaily * 0.75), round(avgDaily * 1.25),
                    "Không có dữ liệu thời tiết", null, null, 1.0,
                    false, isWeekend
            ));
            total += avgDaily;
        }

        double aiRecommendedOrder = Math.max(0.0,
                total + ingredient.getMinStock().doubleValue() - currentStock.doubleValue()
        );
        int historyDaysWithData = (int) history.stream()
                .filter(p -> p.quantity() > 0).count();

        return new AiForecastResponse(
                storeId,
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getCode(),
                ingredient.getUnit(),
                round(total),
                round(avgDaily),
                currentStock.doubleValue(),
                ingredient.getMinStock().doubleValue(),
                round(aiRecommendedOrder),
                breakdown,
                "moving_average",
                historyDaysWithData,
                null,
                "Python Prophet service không khả dụng. Dùng Moving Average 14 ngày gần nhất.",
                true // isJavaFallback
        );
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
