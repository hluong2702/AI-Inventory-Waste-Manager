package vn.inventoryai.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    @EntityGraph(attributePaths = {"store", "ingredient"})
    List<InventoryBatch> findByStoreIdOrderByExpiryDateAsc(Long storeId);

    @EntityGraph(attributePaths = {"store", "ingredient"})
    Page<InventoryBatch> findByStoreId(Long storeId, Pageable pageable);

    @Query("""
            select coalesce(sum(b.quantity), 0)
            from InventoryBatch b
            where b.store.id = :storeId and b.ingredient.id = :ingredientId and b.quantity > 0
            """)
    BigDecimal sumAvailable(Long storeId, Long ingredientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<InventoryBatch> findByStoreIdAndIngredientIdAndQuantityGreaterThanOrderByExpiryDateAscReceivedAtAsc(
            Long storeId,
            Long ingredientId,
            BigDecimal quantity
    );

    List<InventoryBatch> findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
            Long storeId,
            BigDecimal quantity,
            LocalDate expiryDate
    );

    List<InventoryBatch> findByStoreIdAndIngredientIdAndQuantityGreaterThanOrderByExpiryDateAsc(
            Long storeId,
            Long ingredientId,
            BigDecimal quantity
    );
}
