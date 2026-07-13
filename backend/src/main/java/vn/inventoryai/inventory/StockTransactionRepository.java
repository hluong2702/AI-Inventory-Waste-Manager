package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    Optional<StockTransaction> findTopByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<StockTransaction> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long storeId, Instant start, Instant end);
}
