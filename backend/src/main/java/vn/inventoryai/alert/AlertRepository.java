package vn.inventoryai.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.inventoryai.common.enums.AlertType;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    boolean existsByStoreIdAndIngredientIdAndTypeAndResolvedFalse(Long storeId, Long ingredientId, AlertType type);
}
