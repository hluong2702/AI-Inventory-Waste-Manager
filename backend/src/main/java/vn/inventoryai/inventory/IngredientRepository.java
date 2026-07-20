package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByIdAndStoreIdAndDeletedFalse(Long id, Long storeId);

    Optional<Ingredient> findByStoreIdAndCodeAndDeletedFalse(Long storeId, String code);

    boolean existsByStoreIdAndCodeAndDeletedFalse(Long storeId, String code);

    boolean existsByStoreIdAndCodeAndDeletedFalseAndIdNot(Long storeId, String code, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.id = :id and i.store.id = :storeId and i.deleted = false")
    Optional<Ingredient> findActiveByIdAndStoreIdForUpdate(
            @Param("id") Long id,
            @Param("storeId") Long storeId
    );

    List<Ingredient> findByStoreIdAndDeletedFalse(Long storeId);

    Page<Ingredient> findByStoreIdAndDeletedFalse(Long storeId, Pageable pageable);

    long countByStoreIdAndDeletedFalse(Long storeId);

    @Query("""
            select i.code
            from Ingredient i
            where i.store.id = :storeId
              and i.deleted = false
              and i.code in :codes
            """)
    List<String> findExistingActiveCodes(
            @Param("storeId") Long storeId,
            @Param("codes") Collection<String> codes
    );

    @Query("""
            select coalesce(sum(b.quantity), 0)
            from InventoryBatch b
            where b.store.id = :storeId
              and b.ingredient.id = :ingredientId
              and b.quantity > 0
              and b.expiryDate >= current_date
            """)
    BigDecimal currentStock(Long storeId, Long ingredientId);
}
