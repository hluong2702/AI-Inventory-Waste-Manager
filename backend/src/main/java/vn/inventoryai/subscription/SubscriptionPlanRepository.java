package vn.inventoryai.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, Long> {
    Optional<SubscriptionPlanEntity> findByCodeAndActiveTrue(SubscriptionPlan code);

    Optional<SubscriptionPlanEntity> findByCode(SubscriptionPlan code);

    List<SubscriptionPlanEntity> findByActiveTrueOrderByPriceAsc();
}
