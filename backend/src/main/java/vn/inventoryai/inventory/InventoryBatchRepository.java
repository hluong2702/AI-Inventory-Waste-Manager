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
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    @EntityGraph(attributePaths = {"store", "ingredient"})
    List<InventoryBatch> findByStoreIdOrderByExpiryDateAsc(Long storeId);

    @EntityGraph(attributePaths = {"store", "ingredient"})
    Page<InventoryBatch> findByStoreIdAndQuantityGreaterThan(Long storeId, BigDecimal quantity, Pageable pageable);

    @EntityGraph(attributePaths = {"store", "ingredient"})
    Page<InventoryBatch> findByStoreIdAndIngredientIdAndQuantityGreaterThan(
            Long storeId,
            Long ingredientId,
            BigDecimal quantity,
            Pageable pageable
    );

    boolean existsByStoreIdAndIngredientIdAndQuantityGreaterThan(
            Long storeId,
            Long ingredientId,
            BigDecimal quantity
    );

    @Query("""
            select coalesce(sum(b.quantity), 0)
            from InventoryBatch b
            where b.store.id = :storeId
              and b.ingredient.id = :ingredientId
              and b.quantity > 0
              and b.expiryDate >= :businessDate
            """)
    BigDecimal sumSellable(Long storeId, Long ingredientId, LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from InventoryBatch b
            where b.store.id = :storeId
              and b.ingredient.id = :ingredientId
              and b.quantity > 0
              and b.expiryDate >= :businessDate
            order by b.expiryDate asc, b.receivedAt asc, b.id asc
            """)
    List<InventoryBatch> lockSellableFefo(Long storeId, Long ingredientId, LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from InventoryBatch b
            where b.store.id = :storeId
              and b.ingredient.id = :ingredientId
              and b.quantity > 0
            order by b.expiryDate asc, b.receivedAt asc, b.id asc
            """)
    List<InventoryBatch> lockPositiveFefo(Long storeId, Long ingredientId);

    List<InventoryBatch> findByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqual(
            Long storeId,
            BigDecimal quantity,
            LocalDate expiryDate
    );

    @EntityGraph(attributePaths = {"store", "ingredient"})
    List<InventoryBatch> findTop5ByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqualOrderByExpiryDateAscReceivedAtAscIdAsc(
            Long storeId,
            BigDecimal quantity,
            LocalDate expiryDate
    );

    List<InventoryBatch> findByStoreIdAndIngredientIdAndQuantityGreaterThanOrderByExpiryDateAscReceivedAtAscIdAsc(
            Long storeId,
            Long ingredientId,
            BigDecimal quantity
    );

    @Query("""
            select b.ingredient.id as ingredientId, coalesce(sum(b.quantity), 0) as quantity
            from InventoryBatch b
            where b.store.id = :storeId
              and b.quantity > 0
              and b.expiryDate >= :businessDate
            group by b.ingredient.id
            """)
    List<IngredientQuantity> sumSellableGroupedByIngredient(Long storeId, LocalDate businessDate);

    @Query("""
            select b.ingredient.id as ingredientId, coalesce(sum(b.quantity), 0) as quantity
            from InventoryBatch b
            where b.store.id = :storeId
              and b.ingredient.id in :ingredientIds
              and b.quantity > 0
              and b.expiryDate >= :businessDate
            group by b.ingredient.id
            """)
    List<IngredientQuantity> sumSellableGroupedByIngredientIds(
            Long storeId,
            Collection<Long> ingredientIds,
            LocalDate businessDate
    );

    @Query("""
            select b.ingredient.id as ingredientId,
                   b.id as batchId,
                   b.expiryDate as expiryDate,
                   b.receivedAt as receivedAt,
                   b.quantity as quantity
            from InventoryBatch b
            where b.store.id = :storeId
              and b.ingredient.id in :ingredientIds
              and b.quantity > 0
              and b.expiryDate >= :businessDate
              and not exists (
                  select prior.id
                  from InventoryBatch prior
                  where prior.store.id = :storeId
                    and prior.ingredient.id = b.ingredient.id
                    and prior.quantity > 0
                    and prior.expiryDate >= :businessDate
                    and (
                        prior.expiryDate < b.expiryDate
                        or (prior.expiryDate = b.expiryDate and prior.receivedAt < b.receivedAt)
                        or (
                            prior.expiryDate = b.expiryDate
                            and prior.receivedAt = b.receivedAt
                            and prior.id < b.id
                        )
                    )
              )
            order by b.ingredient.id asc
            """)
    List<NearestSellableBatch> findNearestSellableBatchByIngredientIds(
            Long storeId,
            Collection<Long> ingredientIds,
            LocalDate businessDate
    );

    @Query(value = """
            select i.id as ingredientId,
                   i.code as code,
                   i.name as name,
                   i.unit as unit,
                   i.category as category,
                   i.minStock as minStock,
                   i.maxStock as maxStock,
                   coalesce(sum(case when b.quantity > 0 then b.quantity else 0 end), 0) as totalQuantity,
                   coalesce(sum(case when b.quantity > 0 and b.expiryDate >= :businessDate then b.quantity else 0 end), 0) as sellableQuantity,
                   coalesce(sum(case when b.quantity > 0 then 1 else 0 end), 0) as activeBatchesCount,
                   coalesce(sum(case when b.quantity > 0 and b.expiryDate < :businessDate then 1 else 0 end), 0) as expiredBatchesCount,
                   coalesce(sum(case when b.quantity > 0 and b.expiryDate >= :businessDate and b.expiryDate <= :alertDate then 1 else 0 end), 0) as expiringSoonBatchesCount
            from Ingredient i
            left join InventoryBatch b on b.ingredient = i and b.store.id = :storeId
            where i.store.id = :storeId
              and i.deleted = false
            group by i.id, i.code, i.name, i.unit, i.category, i.minStock, i.maxStock
            order by i.name asc, i.id asc
            """, countQuery = """
            select count(i)
            from Ingredient i
            where i.store.id = :storeId
              and i.deleted = false
            """)
    Page<InventorySummaryRow> summarizeInventory(
            Long storeId,
            LocalDate businessDate,
            LocalDate alertDate,
            Pageable pageable
    );

    interface IngredientQuantity {
        Long getIngredientId();

        BigDecimal getQuantity();
    }

    interface NearestSellableBatch {
        Long getIngredientId();

        Long getBatchId();

        LocalDate getExpiryDate();

        Instant getReceivedAt();

        BigDecimal getQuantity();
    }

    interface InventorySummaryRow {
        Long getIngredientId();

        String getCode();

        String getName();

        String getUnit();

        String getCategory();

        BigDecimal getMinStock();

        BigDecimal getMaxStock();

        BigDecimal getTotalQuantity();

        BigDecimal getSellableQuantity();

        Long getActiveBatchesCount();

        Long getExpiredBatchesCount();

        Long getExpiringSoonBatchesCount();
    }
}
