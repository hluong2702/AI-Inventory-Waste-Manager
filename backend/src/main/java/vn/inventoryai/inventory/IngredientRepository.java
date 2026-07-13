package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByIdAndStoreIdAndDeletedFalse(Long id, Long storeId);

    Optional<Ingredient> findByStoreIdAndCodeAndDeletedFalse(Long storeId, String code);

    List<Ingredient> findByStoreIdAndDeletedFalse(Long storeId);

    long countByStoreIdAndDeletedFalse(Long storeId);

    @Query("""
            select coalesce(sum(b.quantity), 0)
            from InventoryBatch b
            where b.store.id = :storeId and b.ingredient.id = :ingredientId
            """)
    BigDecimal currentStock(Long storeId, Long ingredientId);
}
