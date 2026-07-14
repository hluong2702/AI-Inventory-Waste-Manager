package vn.inventoryai.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface WasteRecordRepository extends JpaRepository<WasteRecord, Long> {
    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    List<WasteRecord> findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long storeId, Instant start, Instant end);

    @EntityGraph(attributePaths = {"store", "ingredient", "batch", "createdBy"})
    Page<WasteRecord> findByStoreIdAndCreatedAtBetween(Long storeId, Instant start, Instant end, Pageable pageable);
}
