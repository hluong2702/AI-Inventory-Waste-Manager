package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface WasteRecordRepository extends JpaRepository<WasteRecord, Long> {
    @Query("select coalesce(sum(w.estimatedCost), 0) from WasteRecord w")
    BigDecimal sumEstimatedCostAllStores();
    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    @Query("""
            select w
            from WasteRecord w
            where w.store.id = :storeId
              and w.createdAt >= :start
              and w.createdAt < :end
            """)
    Page<WasteRecord> findReportPage(Long storeId, Instant start, Instant end, Pageable pageable);

    @Query("""
            select coalesce(sum(w.estimatedCost), 0) as totalCost,
                   coalesce(sum(w.quantity), 0) as totalQuantity,
                   count(w) as recordCount,
                   count(distinct w.ingredient.id) as affectedIngredientCount
            from WasteRecord w
            where w.store.id = :storeId
              and w.createdAt >= :start
              and w.createdAt < :end
            """)
    WasteTotals aggregateTotals(Long storeId, Instant start, Instant end);

    @Query("""
            select w.reason as reason,
                   coalesce(sum(w.estimatedCost), 0) as estimatedCost,
                   coalesce(sum(w.quantity), 0) as quantity,
                   count(w) as recordCount
            from WasteRecord w
            where w.store.id = :storeId
              and w.createdAt >= :start
              and w.createdAt < :end
            group by w.reason
            order by sum(w.estimatedCost) desc, w.reason asc
            """)
    List<WasteReasonAggregate> aggregateByReason(Long storeId, Instant start, Instant end);

    @Query("""
            select w.ingredient.id as ingredientId,
                   w.ingredient.name as ingredientName,
                   w.ingredient.unit as unit,
                   coalesce(sum(w.quantity), 0) as quantity,
                   coalesce(sum(w.estimatedCost), 0) as estimatedCost
            from WasteRecord w
            where w.store.id = :storeId
              and w.createdAt >= :start
              and w.createdAt < :end
            group by w.ingredient.id, w.ingredient.name, w.ingredient.unit
            order by sum(w.estimatedCost) desc, w.ingredient.id asc
            """)
    List<WasteIngredientAggregate> aggregateByIngredient(
            Long storeId,
            Instant start,
            Instant end,
            Pageable pageable
    );

    @Query(value = """
            select date(convert_tz(w.created_at, '+00:00', :businessOffset)) as businessDate,
                   coalesce(sum(w.estimated_cost), 0) as estimatedCost
            from waste_records w
            where w.store_id = :storeId
              and w.created_at >= :start
              and w.created_at < :end
            group by date(convert_tz(w.created_at, '+00:00', :businessOffset))
            order by businessDate asc
            """, nativeQuery = true)
    List<WasteDailyAggregate> aggregateDaily(
            @Param("storeId") Long storeId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("businessOffset") String businessOffset
    );

    @Query("""
            select w.id as id,
                   w.createdAt as createdAt,
                   w.store.name as storeName,
                   w.ingredient.name as ingredientName,
                   w.ingredient.unit as unit,
                   batch.batchNumber as batchNumber,
                   w.quantity as quantity,
                   w.reason as reason,
                   w.estimatedCost as estimatedCost,
                   creator.email as recordedBy
            from WasteRecord w
            left join w.batch batch
            left join w.createdBy creator
            where w.store.id = :storeId
              and w.createdAt >= :start
              and w.createdAt < :end
              and (w.createdAt < :cursorCreatedAt
                   or (w.createdAt = :cursorCreatedAt and w.id < :cursorId))
            order by w.createdAt desc, w.id desc
            """)
    List<WasteExportRow> findExportRows(
            Long storeId,
            Instant start,
            Instant end,
            Instant cursorCreatedAt,
            Long cursorId,
            Pageable pageable
    );

    interface WasteTotals {
        BigDecimal getTotalCost();

        BigDecimal getTotalQuantity();

        Long getRecordCount();

        Long getAffectedIngredientCount();
    }

    interface WasteReasonAggregate {
        String getReason();

        BigDecimal getEstimatedCost();

        BigDecimal getQuantity();

        Long getRecordCount();
    }

    interface WasteIngredientAggregate {
        Long getIngredientId();

        String getIngredientName();

        String getUnit();

        BigDecimal getQuantity();

        BigDecimal getEstimatedCost();
    }

    interface WasteDailyAggregate {
        LocalDate getBusinessDate();

        BigDecimal getEstimatedCost();
    }

    interface WasteExportRow {
        Long getId();

        Instant getCreatedAt();

        String getStoreName();

        String getIngredientName();

        String getUnit();

        String getBatchNumber();

        BigDecimal getQuantity();

        String getReason();

        BigDecimal getEstimatedCost();

        String getRecordedBy();
    }
}
