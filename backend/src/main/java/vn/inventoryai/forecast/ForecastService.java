package vn.inventoryai.forecast;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.forecast.dto.ForecastResponse;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForecastService {
    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 100;

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ForecastResponse forecast(Long ingredientId, int days) {
        Long storeId = SecurityUtils.storeId();
        Ingredient ingredient = ingredientRepository.findByIdAndStoreIdAndDeletedFalse(ingredientId, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found in current store"));

        int windowDays = normalizeWindow(days);
        BigDecimal consumedQuantity = transactionRepository.sumQuantitySinceByReason(
                storeId,
                ingredientId,
                StockTransactionType.OUT,
                "EXPORT_CONSUME",
                clock.instant().minus(windowDays, ChronoUnit.DAYS)
        );
        BigDecimal currentStock = batchRepository.sumSellable(storeId, ingredientId, LocalDate.now(clock));
        return buildForecast(storeId, ingredient, windowDays, consumedQuantity, currentStock);
    }

    @Transactional(readOnly = true)
    public Page<ForecastResponse> forecastAll(int days, Pageable requestedPageable) {
        Long storeId = SecurityUtils.storeId();
        int windowDays = normalizeWindow(days);
        Instant from = clock.instant().minus(windowDays, ChronoUnit.DAYS);
        LocalDate businessDate = LocalDate.now(clock);
        Page<Ingredient> ingredientPage = ingredientRepository.findByStoreIdAndDeletedFalse(
                storeId,
                boundedPageable(requestedPageable)
        );
        if (ingredientPage.isEmpty()) {
            return ingredientPage.map(ingredient -> buildForecast(
                    storeId,
                    ingredient,
                    windowDays,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            ));
        }
        Collection<Long> ingredientIds = ingredientPage.getContent().stream()
                .map(Ingredient::getId)
                .toList();

        Map<Long, BigDecimal> consumedByIngredient = transactionRepository
                .sumQuantitySinceGroupedByIngredientIds(
                        storeId,
                        ingredientIds,
                        StockTransactionType.OUT,
                        "EXPORT_CONSUME",
                        from
                )
                .stream()
                .collect(Collectors.toMap(
                        StockTransactionRepository.IngredientQuantity::getIngredientId,
                        StockTransactionRepository.IngredientQuantity::getQuantity,
                        BigDecimal::add
                ));
        Map<Long, BigDecimal> stockByIngredient = batchRepository
                .sumSellableGroupedByIngredientIds(storeId, ingredientIds, businessDate)
                .stream()
                .collect(Collectors.toMap(
                        InventoryBatchRepository.IngredientQuantity::getIngredientId,
                        InventoryBatchRepository.IngredientQuantity::getQuantity,
                        BigDecimal::add
                ));

        return ingredientPage.map(ingredient -> buildForecast(
                        storeId,
                        ingredient,
                        windowDays,
                        consumedByIngredient.getOrDefault(ingredient.getId(), BigDecimal.ZERO),
                        stockByIngredient.getOrDefault(ingredient.getId(), BigDecimal.ZERO)
                ));
    }

    private ForecastResponse buildForecast(
            Long storeId,
            Ingredient ingredient,
            int windowDays,
            BigDecimal consumedQuantity,
            BigDecimal currentStock
    ) {
        BigDecimal avgDailyUsage = consumedQuantity.divide(BigDecimal.valueOf(windowDays), 3, RoundingMode.HALF_UP);
        BigDecimal recommended = avgDailyUsage.multiply(BigDecimal.valueOf(windowDays))
                .add(ingredient.getMinStock())
                .subtract(currentStock)
                .max(BigDecimal.ZERO);

        return new ForecastResponse(
                storeId,
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getCode(),
                ingredient.getUnit(),
                avgDailyUsage,
                currentStock,
                ingredient.getMinStock(),
                recommended
        );
    }

    private int normalizeWindow(int days) {
        return Math.min(Math.max(days, 1), 90);
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
}
