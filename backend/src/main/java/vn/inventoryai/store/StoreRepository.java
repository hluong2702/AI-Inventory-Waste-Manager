package vn.inventoryai.store;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.time.Instant;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {
    long countByStatus(StoreStatus status);

    long countByCreatedAtAfter(Instant from);

    List<Store> findBySubscriptionPlanAndStatus(SubscriptionPlan plan, StoreStatus status);

    List<Store> findBySubscriptionPlan(SubscriptionPlan plan);

    List<Store> findByStatus(StoreStatus status);

    List<Store> findByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, StoreStatus status);
}
