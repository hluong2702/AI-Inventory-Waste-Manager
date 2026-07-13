package vn.inventoryai.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByStoreId(Long storeId);

    long countByActiveTrueAndExpiresAtBetween(LocalDate from, LocalDate to);
}
