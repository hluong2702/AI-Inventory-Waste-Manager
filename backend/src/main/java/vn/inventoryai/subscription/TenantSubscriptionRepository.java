package vn.inventoryai.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from TenantSubscription s
            join fetch s.plan
            where s.tenant.id = :tenantId and s.status = 'ACTIVE'
            """)
    Optional<TenantSubscription> findActiveForUpdate(Long tenantId);

    @Query("""
            select s from TenantSubscription s
            join fetch s.plan
            where s.tenant.id = :tenantId and s.status = 'ACTIVE'
            """)
    Optional<TenantSubscription> findActive(Long tenantId);

    Optional<TenantSubscription> findFirstByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from TenantSubscription s
            join fetch s.plan
            where s.tenant.id = :tenantId and s.status = 'PENDING_PAYMENT'
            """)
    List<TenantSubscription> findPendingForUpdate(Long tenantId);

    List<TenantSubscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDate date);

    List<TenantSubscription> findByStatusAndEndDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TenantSubscription s join fetch s.plan join fetch s.tenant where s.id = :id")
    Optional<TenantSubscription> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(s) > 0 from TenantSubscription s
            where s.id = :subscriptionId
              and s.tenant.id = :tenantId
              and s.status = 'PENDING_PAYMENT'
            """)
    boolean isCurrentPending(
            @Param("tenantId") Long tenantId,
            @Param("subscriptionId") Long subscriptionId
    );

    @Query(value = """
            select coalesce(sum(
                case
                    when plan.billing_cycle = 'YEARLY' then plan.price / 12
                    else plan.price
                end
            ), 0)
            from tenant_subscriptions subscription
            join subscription_plans plan on plan.id = subscription.plan_id
            where subscription.status = 'ACTIVE'
              and plan.code <> 'FREE'
            """, nativeQuery = true)
    BigDecimal sumActiveMonthlyRecurringRevenue();

    long countByStatusAndEndDateBetween(SubscriptionStatus status, LocalDate from, LocalDate to);
}
