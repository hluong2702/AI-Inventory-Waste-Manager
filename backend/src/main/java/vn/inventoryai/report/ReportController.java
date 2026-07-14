package vn.inventoryai.report;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.StockTransaction;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.inventory.WasteRecord;
import vn.inventoryai.inventory.WasteRecordRepository;
import vn.inventoryai.report.dto.AuditLogResponse;
import vn.inventoryai.report.dto.StockTransactionResponse;
import vn.inventoryai.report.dto.WasteDashboardResponse;
import vn.inventoryai.report.dto.WasteRecordResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final StockTransactionRepository transactionRepository;
    private final WasteRecordRepository wasteRecordRepository;

    @GetMapping("/waste")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<WasteRecordResponse> waste(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable
    ) {
        Long storeId = SecurityUtils.storeId();
        Instant start = startDate == null ? Instant.EPOCH : startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate == null ? Instant.now().plus(1, ChronoUnit.DAYS) : endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return wasteRecordRepository.findByStoreIdAndCreatedAtBetween(storeId, start, end, pageable).map(this::toWasteResponse);
    }

    @GetMapping("/waste/export")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    ResponseEntity<String> exportWaste(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        StringBuilder csv = new StringBuilder("\uFEFFcreatedAt,store,ingredient,batch,quantity,unit,reason,estimatedCost,recordedBy\r\n");
        wasteRecordRepository.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(SecurityUtils.storeId(),
                startDate == null ? Instant.EPOCH : startDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                endDate == null ? Instant.now().plus(1, ChronoUnit.DAYS) : endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
                .stream().map(this::toWasteResponse).forEach(w -> CsvEscaper.appendRow(csv, w.createdAt(), SecurityUtils.storeId(), w.ingredientId(),
                w.batchId(), w.quantity(), "", w.reason(), w.estimatedCost(), w.recordedBy()));
        return csv("waste-report.csv", csv.toString());
    }

    @GetMapping("/inventory/export")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    ResponseEntity<String> exportInventory(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        Long storeId = SecurityUtils.storeId();
        Instant start = startDate == null ? Instant.EPOCH : startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate == null ? Instant.now().plus(1, ChronoUnit.DAYS) : endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        StringBuilder csv = new StringBuilder("\uFEFFcreatedAt,store,ingredient,batch,type,reason,quantity,unitCost,recordedBy\r\n");
        transactionRepository.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(storeId, start, end).forEach(tx ->
                CsvEscaper.appendRow(csv, tx.getCreatedAt(), tx.getStore().getName(), tx.getIngredient().getName(),
                        tx.getBatch() == null ? "" : tx.getBatch().getBatchNumber(), tx.getType().name(), tx.getReason(),
                        tx.getQuantity(), tx.getUnitCost(), tx.getCreatedBy().getEmail()));
        return csv("inventory-transactions.csv", csv.toString());
    }

    @GetMapping("/waste/dashboard")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    WasteDashboardResponse wasteDashboard(@RequestParam(defaultValue = "month") String period) {
        Long storeId = SecurityUtils.storeId();
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate currentStart = "week".equals(period) ? now.minusDays(6) : now.withDayOfMonth(1);
        LocalDate previousStart = "week".equals(period) ? currentStart.minusDays(7) : currentStart.minusMonths(1);
        LocalDate previousEnd = currentStart.minusDays(1);

        List<WasteRecord> current = wasteRecordRepository.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                storeId,
                currentStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                now.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        List<WasteRecord> previous = wasteRecordRepository.findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                storeId,
                previousStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                previousEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );

        BigDecimal currentCost = sumWaste(current);
        BigDecimal previousCost = sumWaste(previous);
        BigDecimal change = previousCost.signum() == 0
                ? BigDecimal.ZERO
                : currentCost.subtract(previousCost).multiply(BigDecimal.valueOf(100)).divide(previousCost, 2, RoundingMode.HALF_UP);

        Map<Long, WasteDashboardResponse.TopWasteIngredient> grouped = new LinkedHashMap<>();
        for (WasteRecord record : current) {
            WasteDashboardResponse.TopWasteIngredient existing = grouped.get(record.getIngredient().getId());
            BigDecimal quantity = existing == null ? record.getQuantity() : existing.quantity().add(record.getQuantity());
            BigDecimal cost = existing == null ? record.getEstimatedCost() : existing.estimatedCost().add(record.getEstimatedCost());
            grouped.put(record.getIngredient().getId(), new WasteDashboardResponse.TopWasteIngredient(
                    record.getIngredient().getId(),
                    record.getIngredient().getName(),
                    record.getIngredient().getUnit(),
                    quantity,
                    cost
            ));
        }

        List<WasteDashboardResponse.TopWasteIngredient> top = grouped.values().stream()
                .sorted(Comparator.comparing(WasteDashboardResponse.TopWasteIngredient::estimatedCost).reversed())
                .limit(5)
                .toList();
        return new WasteDashboardResponse(period, currentCost, previousCost, change, top);
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Page<AuditLogResponse> auditLog(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable
    ) {
        Long storeId = SecurityUtils.storeId();
        Instant start = startDate == null ? Instant.EPOCH : startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate == null ? Instant.now().plus(1, ChronoUnit.DAYS) : endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return transactionRepository.findByStoreIdAndCreatedAtBetween(storeId, start, end, pageable)
                .map(tx -> new AuditLogResponse(
                        tx.getId(),
                        tx.getCreatedAt(),
                        tx.getStore().getId(),
                        tx.getStore().getName(),
                        tx.getIngredient().getId(),
                        tx.getIngredient().getName(),
                        tx.getBatch() == null ? "" : tx.getBatch().getBatchNumber(),
                        tx.getType().name(),
                        tx.getReason(),
                        tx.getQuantity(),
                        tx.getIngredient().getUnit(),
                        tx.getCreatedBy().getEmail()
                ));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    Page<StockTransactionResponse> transactions(Pageable pageable) {
        Long storeId = SecurityUtils.storeId();
        return transactionRepository.findByStoreId(storeId, pageable)
                .map(tx -> new StockTransactionResponse(
                        tx.getId(),
                        storeId,
                        StockTransactionResponse.legacyType(tx.getType()),
                        tx.getReason(),
                        tx.getCreatedAt(),
                        tx.getCreatedBy().getEmail(),
                        List.of(new StockTransactionResponse.Item(
                                tx.getIngredient().getId(),
                                tx.getBatch() == null ? "" : tx.getBatch().getBatchNumber(),
                                tx.getBatch() == null ? null : tx.getBatch().getId(),
                                tx.getQuantity(),
                                tx.getBatch() == null ? null : tx.getBatch().getExpiryDate(),
                                tx.getUnitCost()
                        ))
                ));
    }

    private WasteRecordResponse toWasteResponse(WasteRecord w) {
        return new WasteRecordResponse(
                w.getId(),
                w.getStore().getId(),
                w.getIngredient().getId(),
                w.getBatch() == null ? null : w.getBatch().getId(),
                w.getQuantity(),
                w.getReason(),
                w.getEstimatedCost(),
                w.getCreatedBy() == null ? null : w.getCreatedBy().getEmail(),
                w.getCreatedAt()
        );
    }

    private BigDecimal sumWaste(List<WasteRecord> records) {
        return records.stream().map(WasteRecord::getEstimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ResponseEntity<String> csv(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(content);
    }

}
