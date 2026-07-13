package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WasteRecordRepository extends JpaRepository<WasteRecord, Long> {
    List<WasteRecord> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long storeId, Instant start, Instant end);
}
