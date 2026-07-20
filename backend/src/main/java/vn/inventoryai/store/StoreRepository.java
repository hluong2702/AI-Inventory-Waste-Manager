package vn.inventoryai.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.time.Instant;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {
    long countByStatus(StoreStatus status);

    long countByCreatedAtAfter(Instant from);

    List<Store> findBySubscriptionPlanAndStatus(SubscriptionPlan plan, StoreStatus status);
    Page<Store> findBySubscriptionPlanAndStatus(SubscriptionPlan plan, StoreStatus status, Pageable pageable);

    List<Store> findBySubscriptionPlan(SubscriptionPlan plan);
    Page<Store> findBySubscriptionPlan(SubscriptionPlan plan, Pageable pageable);

    List<Store> findByStatus(StoreStatus status);
    Page<Store> findByStatus(StoreStatus status, Pageable pageable);

    @Query("""
            select s.id
            from Store s
            where s.status = :status
              and s.id > :afterId
            order by s.id
            """)
    List<Long> findIdsByStatusAfter(
            @Param("status") StoreStatus status,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    List<Store> findByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, StoreStatus status);
}
