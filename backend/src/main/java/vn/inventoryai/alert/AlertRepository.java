package vn.inventoryai.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    @EntityGraph(attributePaths = {"store", "ingredient"})
    List<Alert> findByStoreIdAndResolvedFalseOrderByCreatedAtDesc(Long storeId);

    long countByStoreIdAndResolvedFalse(Long storeId);

    @EntityGraph(attributePaths = {"store", "ingredient"})
    List<Alert> findTop3ByStoreIdAndResolvedFalseOrderByCreatedAtDesc(Long storeId);

    @EntityGraph(attributePaths = {"store", "ingredient"})
    Optional<Alert> findByIdAndStoreId(Long id, Long storeId);

    @Modifying
    @Query(value = """
            UPDATE alerts alert_row
            JOIN ingredients ingredient
              ON ingredient.id = alert_row.ingredient_id
             AND ingredient.store_id = alert_row.store_id
            LEFT JOIN (
                SELECT batch.store_id,
                       batch.ingredient_id,
                       SUM(batch.quantity) AS sellable_quantity
                FROM inventory_batches batch
                WHERE batch.store_id = :storeId
                  AND batch.quantity > 0
                  AND batch.expiry_date >= :businessDate
                GROUP BY batch.store_id, batch.ingredient_id
            ) stock
              ON stock.store_id = alert_row.store_id
             AND stock.ingredient_id = alert_row.ingredient_id
            SET alert_row.resolved = TRUE
            WHERE alert_row.store_id = :storeId
              AND alert_row.type = 'LOW_STOCK'
              AND alert_row.resolved = FALSE
              AND (
                  ingredient.is_deleted = TRUE
                  OR COALESCE(stock.sellable_quantity, 0) >= ingredient.min_stock
              )
            """, nativeQuery = true)
    int resolveRecoveredLowStock(
            @Param("storeId") Long storeId,
            @Param("businessDate") LocalDate businessDate
    );

    @Modifying
    @Query(value = """
            UPDATE alerts alert_row
            JOIN ingredients ingredient
              ON ingredient.id = alert_row.ingredient_id
             AND ingredient.store_id = alert_row.store_id
            SET alert_row.resolved = TRUE
            WHERE alert_row.store_id = :storeId
              AND alert_row.type = 'EXPIRING_SOON'
              AND alert_row.resolved = FALSE
              AND (
                  ingredient.is_deleted = TRUE
                  OR NOT EXISTS (
                      SELECT 1
                      FROM inventory_batches batch
                      WHERE batch.store_id = alert_row.store_id
                        AND batch.ingredient_id = alert_row.ingredient_id
                        AND batch.quantity > 0
                        AND batch.expiry_date <= :expiryThreshold
                  )
              )
            """, nativeQuery = true)
    int resolveClearedExpiryRisk(
            @Param("storeId") Long storeId,
            @Param("expiryThreshold") LocalDate expiryThreshold
    );

    @Modifying
    @Query(value = """
            INSERT INTO alerts (store_id, type, ingredient_id, resolved, created_at)
            SELECT ingredient.store_id,
                   'LOW_STOCK',
                   ingredient.id,
                   FALSE,
                   CURRENT_TIMESTAMP
            FROM ingredients ingredient
            JOIN stores store
              ON store.id = ingredient.store_id
             AND store.status = 'ACTIVE'
            LEFT JOIN inventory_batches batch
              ON batch.store_id = ingredient.store_id
             AND batch.ingredient_id = ingredient.id
             AND batch.quantity > 0
             AND batch.expiry_date >= :businessDate
            WHERE ingredient.store_id = :storeId
              AND ingredient.is_deleted = FALSE
            GROUP BY ingredient.store_id, ingredient.id, ingredient.min_stock
            HAVING COALESCE(SUM(batch.quantity), 0) < ingredient.min_stock
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int createMissingLowStock(
            @Param("storeId") Long storeId,
            @Param("businessDate") LocalDate businessDate
    );

    @Modifying
    @Query(value = """
            INSERT INTO alerts (store_id, type, ingredient_id, resolved, created_at)
            SELECT batch.store_id,
                   'EXPIRING_SOON',
                   batch.ingredient_id,
                   FALSE,
                   CURRENT_TIMESTAMP
            FROM inventory_batches batch
            JOIN ingredients ingredient
              ON ingredient.id = batch.ingredient_id
             AND ingredient.store_id = batch.store_id
             AND ingredient.is_deleted = FALSE
            JOIN stores store
              ON store.id = batch.store_id
             AND store.status = 'ACTIVE'
            WHERE batch.store_id = :storeId
              AND batch.quantity > 0
              AND batch.expiry_date <= :expiryThreshold
            GROUP BY batch.store_id, batch.ingredient_id
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int createMissingExpiryRisk(
            @Param("storeId") Long storeId,
            @Param("expiryThreshold") LocalDate expiryThreshold
    );
}
