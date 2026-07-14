package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.inventoryai.common.enums.StockTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    @Query("""
            select coalesce(sum(t.quantity), 0)
            from StockTransaction t
            where t.store.id = :storeId
              and t.ingredient.id = :ingredientId
              and t.type = :type
              and t.createdAt >= :from
            """)
    BigDecimal sumQuantitySince(Long storeId, Long ingredientId, StockTransactionType type, Instant from);

    List<StockTransaction> findByStoreIdAndIngredientIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            Long storeId,
            Long ingredientId,
            StockTransactionType type,
            Instant from
    );

    long countByStoreId(Long storeId);

    @Query("select t.store.id, count(t) from StockTransaction t group by t.store.id")
    List<Object[]> countGroupedByStoreId();

    Optional<StockTransaction> findTopByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<StockTransaction> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long storeId, Instant start, Instant end);

    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    List<StockTransaction> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    Page<StockTransaction> findByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    Page<StockTransaction> findByStoreIdAndCreatedAtBetween(Long storeId, Instant start, Instant end, Pageable pageable);
}
