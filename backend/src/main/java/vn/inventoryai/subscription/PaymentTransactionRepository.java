package vn.inventoryai.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p join fetch p.subscription s join fetch s.plan where p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    @Query("select p from PaymentTransaction p join fetch p.subscription s join fetch s.plan where p.tenant.id = :tenantId and p.idempotencyKey = :idempotencyKey")
    Optional<PaymentTransaction> findByTenantIdAndIdempotencyKey(
            @Param("tenantId") Long tenantId,
            @Param("idempotencyKey") String idempotencyKey
    );

    List<PaymentTransaction> findBySubscriptionIdAndStatusIn(Long subscriptionId, List<PaymentStatus> statuses);

    List<PaymentTransaction> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant createdAt);

    @Query("""
            select p.id from PaymentTransaction p
            where p.status = 'PENDING' and p.createdAt < :createdBefore
            order by p.createdAt, p.id
            """)
    List<Long> findReconciliationCandidates(@Param("createdBefore") Instant createdBefore, Pageable pageable);

    @Query("""
            select p.id from PaymentTransaction p
            where p.status = 'CREATING' and p.createdAt < :createdBefore
            order by p.createdAt, p.id
            """)
    List<Long> findCreationReconciliationCandidates(@Param("createdBefore") Instant createdBefore, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentTransaction p
            set p.status = 'RECONCILING', p.updatedAt = :now
            where p.id = :id and p.status = 'PENDING'
            """)
    int claimForReconciliation(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentTransaction p
            set p.status = 'CREATION_RECONCILING', p.updatedAt = :now
            where p.id = :id and p.status = 'CREATING'
            """)
    int claimCreationForReconciliation(@Param("id") Long id, @Param("now") Instant now);
}
