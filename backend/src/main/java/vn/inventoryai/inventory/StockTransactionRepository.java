package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.inventoryai.common.enums.StockTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    @Query("""
            select coalesce(sum(t.quantity), 0)
            from StockTransaction t
            where t.store.id = :storeId
              and t.ingredient.id = :ingredientId
              and t.type = :type
              and t.reason = :reason
              and t.createdAt >= :from
            """)
    BigDecimal sumQuantitySinceByReason(
            Long storeId,
            Long ingredientId,
            StockTransactionType type,
            String reason,
            Instant from
    );

    List<StockTransaction> findByStoreIdAndIngredientIdAndTypeAndReasonAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            Long storeId,
            Long ingredientId,
            StockTransactionType type,
            String reason,
            Instant from
    );

    @Query("""
            select t.ingredient.id as ingredientId, coalesce(sum(t.quantity), 0) as quantity
            from StockTransaction t
            where t.store.id = :storeId
              and t.type = :type
              and t.reason = :reason
              and t.createdAt >= :from
            group by t.ingredient.id
            """)
    List<IngredientQuantity> sumQuantitySinceGroupedByIngredient(
            Long storeId,
            StockTransactionType type,
            String reason,
            Instant from
    );

    @Query("""
            select t.ingredient.id as ingredientId, coalesce(sum(t.quantity), 0) as quantity
            from StockTransaction t
            where t.store.id = :storeId
              and t.ingredient.id in :ingredientIds
              and t.type = :type
              and t.reason = :reason
              and t.createdAt >= :from
            group by t.ingredient.id
            """)
    List<IngredientQuantity> sumQuantitySinceGroupedByIngredientIds(
            Long storeId,
            Collection<Long> ingredientIds,
            StockTransactionType type,
            String reason,
            Instant from
    );

    @Query(value = """
            select t.ingredient_id as ingredientId,
                   coalesce(sum(case when t.created_at >= :from7d then t.quantity else 0 end), 0) as quantity7d,
                   coalesce(sum(t.quantity), 0) as quantity28d,
                   count(distinct date(convert_tz(t.created_at, '+00:00', :businessOffset))) as historyDayCount,
                   coalesce(sum(
                       case
                           when dayofweek(convert_tz(t.created_at, '+00:00', :businessOffset)) = :mysqlDayOfWeek
                           then t.quantity
                           else 0
                       end
                   ), 0) as weekdayQuantity,
                   count(distinct
                       case
                           when dayofweek(convert_tz(t.created_at, '+00:00', :businessOffset)) = :mysqlDayOfWeek
                           then date(convert_tz(t.created_at, '+00:00', :businessOffset))
                           else null
                       end
                   ) as weekdayDayCount
            from stock_transactions t
            where t.store_id = :storeId
              and t.ingredient_id in (:ingredientIds)
              and t.type = 'OUT'
              and t.reason = 'EXPORT_CONSUME'
              and t.created_at >= :from28d
            group by t.ingredient_id
            """, nativeQuery = true)
    List<IngredientUsageMetrics> aggregateConsumptionMetrics(
            Long storeId,
            Collection<Long> ingredientIds,
            Instant from7d,
            Instant from28d,
            int mysqlDayOfWeek,
            String businessOffset
    );

    long countByStoreId(Long storeId);

    @Query("select t.store.id, count(t) from StockTransaction t group by t.store.id")
    List<Object[]> countGroupedByStoreId();

    @Query("""
            select t.store.id as storeId, t.store.name as storeName, count(t) as transactionCount
            from StockTransaction t
            group by t.store.id, t.store.name
            order by count(t) desc, t.store.id asc
            """)
    List<StoreActivity> findMostActiveStores(Pageable pageable);

    Optional<StockTransaction> findTopByStoreIdOrderByCreatedAtDesc(Long storeId);

    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    Page<StockTransaction> findByStoreId(Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    @Query("""
            select t
            from StockTransaction t
            where t.store.id = :storeId
              and t.createdAt >= :start
              and t.createdAt < :end
            """)
    Page<StockTransaction> findAuditPage(Long storeId, Instant start, Instant end, Pageable pageable);

    @Query("""
            select t.id as id,
                   t.createdAt as createdAt,
                   t.store.name as storeName,
                   t.ingredient.name as ingredientName,
                   t.ingredient.unit as unit,
                   batch.batchNumber as batchNumber,
                   t.type as type,
                   t.reason as reason,
                   t.quantity as quantity,
                   t.unitCost as unitCost,
                   t.createdBy.email as recordedBy
            from StockTransaction t
            left join t.batch batch
            where t.store.id = :storeId
              and t.createdAt >= :start
              and t.createdAt < :end
              and (t.createdAt < :cursorCreatedAt
                   or (t.createdAt = :cursorCreatedAt and t.id < :cursorId))
            order by t.createdAt desc, t.id desc
            """)
    List<StockExportRow> findExportRows(
            Long storeId,
            Instant start,
            Instant end,
            Instant cursorCreatedAt,
            Long cursorId,
            Pageable pageable
    );

    interface IngredientQuantity {
        Long getIngredientId();

        BigDecimal getQuantity();
    }

    interface IngredientUsageMetrics {
        Long getIngredientId();

        BigDecimal getQuantity7d();

        BigDecimal getQuantity28d();

        Long getHistoryDayCount();

        BigDecimal getWeekdayQuantity();

        Long getWeekdayDayCount();
    }

    interface StoreActivity {
        Long getStoreId();

        String getStoreName();

        Long getTransactionCount();
    }

    interface StockExportRow {
        Long getId();

        Instant getCreatedAt();

        String getStoreName();

        String getIngredientName();

        String getUnit();

        String getBatchNumber();

        StockTransactionType getType();

        String getReason();

        BigDecimal getQuantity();

        BigDecimal getUnitCost();

        String getRecordedBy();
    }
}
