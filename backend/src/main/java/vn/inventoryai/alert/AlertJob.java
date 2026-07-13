package vn.inventoryai.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.AlertType;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatch;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AlertJob {
    private final StoreRepository storeRepository;
    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final AlertRepository alertRepository;

    @Value("${app.alerts.expiring-days:3}")
    private long expiringDays;

    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void generateDailyAlerts() {
        storeRepository.findAll().stream()
                .filter(store -> store.getStatus() == StoreStatus.ACTIVE)
                .forEach(this::generateForStore);
    }

    private void generateForStore(Store store) {
        Long storeId = store.getId();
        LocalDate threshold = LocalDate.now().plusDays(expiringDays);
        for (InventoryBatch batch : batchRepository.findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(storeId, BigDecimal.ZERO, threshold)) {
            createIfAbsent(store, batch.getIngredient(), AlertType.EXPIRING_SOON);
        }

        for (Ingredient ingredient : ingredientRepository.findByStoreIdAndDeletedFalse(storeId)) {
            BigDecimal currentStock = ingredientRepository.currentStock(storeId, ingredient.getId());
            if (currentStock.compareTo(ingredient.getMinStock()) < 0) {
                createIfAbsent(store, ingredient, AlertType.LOW_STOCK);
            }
        }
    }

    private void createIfAbsent(Store store, Ingredient ingredient, AlertType type) {
        if (alertRepository.existsByStoreIdAndIngredientIdAndTypeAndResolvedFalse(store.getId(), ingredient.getId(), type)) {
            return;
        }
        Alert alert = new Alert();
        alert.setStore(store);
        alert.setIngredient(ingredient);
        alert.setType(type);
        alert.setResolved(false);
        alertRepository.save(alert);
    }
}
