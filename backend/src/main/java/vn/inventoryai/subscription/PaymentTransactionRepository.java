package vn.inventoryai.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByProviderAndProviderTransactionId(String provider, String providerTransactionId);

    List<PaymentTransaction> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant createdAt);
}
