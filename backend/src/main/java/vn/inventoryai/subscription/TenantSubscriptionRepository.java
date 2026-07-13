package vn.inventoryai.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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
}
