package vn.inventoryai.daily;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DailyActionRepository extends JpaRepository<DailyAction, Long> {

    /**
     * Lấy danh sách hành động OPEN của tenant, sắp xếp theo priority_score giảm dần.
     * Dùng EntityGraph để tránh N+1 query khi truy cập product và batch.
     */
    @EntityGraph(attributePaths = {"product", "batch"})
    Page<DailyAction> findByTenantIdAndStatusOrderByPriorityScoreDesc(
            Long tenantId,
            DailyActionStatus status,
            Pageable pageable
    );

    /**
     * Lấy một hành động cụ thể theo id và tenantId để đảm bảo an toàn multi-tenant.
     */
    @EntityGraph(attributePaths = {"product", "batch"})
    Optional<DailyAction> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Đếm số hành động OPEN của tenant (dùng cho badge/count trên UI).
     */
    long countByTenantIdAndStatus(Long tenantId, DailyActionStatus status);

    /**
     * Tìm hành động OPEN theo khóa tái tính toán:
     * (tenant_id, action_type, product_id, batch_id).
     * Dùng để upsert thay vì tạo trùng lặp.
     */
    @Query("""
            select a from DailyAction a
            where a.tenantId = :tenantId
              and a.actionType = :actionType
              and a.product.id = :productId
              and (:batchId is null and a.batch is null or a.batch.id = :batchId)
              and a.status = 'OPEN'
            """)
    Optional<DailyAction> findOpenByRecomputeKey(
            @Param("tenantId") Long tenantId,
            @Param("actionType") DailyActionType actionType,
            @Param("productId") Long productId,
            @Param("batchId") Long batchId
    );

    /**
     * Tìm tất cả hành động OPEN hoặc ACKNOWLEDGED của tenant theo loại và sản phẩm.
     * Dùng khi tái tính toán để kiểm tra hành động nào còn hợp lệ.
     */
    @Query("""
            select a from DailyAction a
            where a.tenantId = :tenantId
              and a.actionType = :actionType
              and a.status in ('OPEN', 'ACKNOWLEDGED')
            """)
    List<DailyAction> findActiveByTenantIdAndActionType(
            @Param("tenantId") Long tenantId,
            @Param("actionType") DailyActionType actionType
    );

    /**
     * Tự động giải quyết (RESOLVED) các hành động EXPIRY_RISK mà lô hàng liên quan
     * không còn rủi ro nữa (đã được dùng hết hoặc HSD đã qua ngưỡng cảnh báo).
     * Được gọi khi bắt đầu mỗi lần tái tính toán.
     */
    @Modifying
    @Query(value = """
            UPDATE daily_actions da
            LEFT JOIN inventory_batches ib ON ib.id = da.batch_id
            SET da.status = 'RESOLVED', da.resolved_at = NOW(), da.updated_at = NOW()
            WHERE da.tenant_id = :tenantId
              AND da.action_type = 'EXPIRY_RISK'
              AND da.status IN ('OPEN', 'ACKNOWLEDGED')
              AND (
                  ib.id IS NULL
                  OR ib.quantity <= 0
                  OR ib.expiry_date > :expiryThreshold
              )
            """, nativeQuery = true)
    int autoResolveStaleExpiryRisk(
            @Param("tenantId") Long tenantId,
            @Param("expiryThreshold") java.time.LocalDate expiryThreshold
    );

    /**
     * Tự động giải quyết các hành động REORDER mà tồn kho đã đủ (về trên min_stock).
     */
    @Modifying
    @Query(value = """
            UPDATE daily_actions da
            JOIN ingredients i ON i.id = da.product_id AND i.store_id = da.tenant_id
            LEFT JOIN (
                SELECT batch.store_id, batch.ingredient_id, SUM(batch.quantity) AS sellable_qty
                FROM inventory_batches batch
                WHERE batch.store_id = :tenantId
                  AND batch.quantity > 0
                  AND batch.expiry_date >= :businessDate
                GROUP BY batch.store_id, batch.ingredient_id
            ) stock ON stock.store_id = da.tenant_id AND stock.ingredient_id = da.product_id
            SET da.status = 'RESOLVED', da.resolved_at = NOW(), da.updated_at = NOW()
            WHERE da.tenant_id = :tenantId
              AND da.action_type = 'REORDER'
              AND da.status IN ('OPEN', 'ACKNOWLEDGED')
              AND COALESCE(stock.sellable_qty, 0) >= i.min_stock
            """, nativeQuery = true)
    int autoResolveStaleReorder(
            @Param("tenantId") Long tenantId,
            @Param("businessDate") java.time.LocalDate businessDate
    );

    /**
     * Xóa các hành động RESOLVED hoặc DISMISSED cũ hơn ngưỡng thời gian.
     * Giúp bảng không phình to theo thời gian.
     */
    @Modifying
    @Query("""
            delete from DailyAction a
            where a.tenantId = :tenantId
              and a.status in ('RESOLVED', 'DISMISSED')
              and a.updatedAt < :threshold
            """)
    int deleteStaleClosedActions(
            @Param("tenantId") Long tenantId,
            @Param("threshold") Instant threshold
    );
}
