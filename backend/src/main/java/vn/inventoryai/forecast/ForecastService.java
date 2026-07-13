package vn.inventoryai.forecast;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.forecast.dto.ForecastResponse;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ForecastService {
    private final IngredientRepository ingredientRepository;
    private final StockTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public ForecastResponse forecast(Long ingredientId, int days) {
        Long storeId = SecurityUtils.storeId();
        Ingredient ingredient = ingredientRepository.findByIdAndStoreIdAndDeletedFalse(ingredientId, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found in current store"));

        int windowDays = Math.max(days, 1);
        BigDecimal outQuantity = transactionRepository.sumQuantitySince(
                storeId,
                ingredientId,
                StockTransactionType.OUT,
                Instant.now().minus(windowDays, ChronoUnit.DAYS)
        );
        BigDecimal avgDailyUsage = outQuantity.divide(BigDecimal.valueOf(windowDays), 3, RoundingMode.HALF_UP);
        BigDecimal currentStock = ingredientRepository.currentStock(storeId, ingredientId);
        BigDecimal recommended = avgDailyUsage.multiply(BigDecimal.valueOf(windowDays))
                .add(ingredient.getMinStock())
                .subtract(currentStock)
                .max(BigDecimal.ZERO);

        return new ForecastResponse(
                storeId,
                ingredientId,
                ingredient.getName(),
                "ING-" + ingredientId,
                ingredient.getUnit(),
                avgDailyUsage,
                currentStock,
                ingredient.getMinStock(),
                recommended
        );
    }

    @Transactional(readOnly = true)
    public List<ForecastResponse> forecastAll(int days) {
        Long storeId = SecurityUtils.storeId();
        return ingredientRepository.findByStoreIdAndDeletedFalse(storeId).stream()
                .map(ingredient -> forecast(ingredient.getId(), days))
                .toList();
    }
}
