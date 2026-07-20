package vn.inventoryai.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.inventory.WasteRecord;
import vn.inventoryai.inventory.WasteRecordRepository;
import vn.inventoryai.report.dto.AuditLogResponse;
import vn.inventoryai.report.dto.StockTransactionResponse;
import vn.inventoryai.report.dto.WasteDashboardResponse;
import vn.inventoryai.report.dto.WasteRecordResponse;
import vn.inventoryai.report.dto.WasteReportSummaryResponse;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {
    static final int MAX_REPORT_RANGE_DAYS = 366;
    static final int MAX_PAGE_SIZE = 100;
    static final int EXPORT_CHUNK_SIZE = 500;

    private static final Set<String> WASTE_SORT_FIELDS = Set.of(
            "createdAt", "estimatedCost", "quantity", "reason", "id"
    );
    private static final Set<String> TRANSACTION_SORT_FIELDS = Set.of(
            "createdAt", "quantity", "type", "reason", "id"
    );

    private final StockTransactionRepository transactionRepository;
    private final WasteRecordRepository wasteRecordRepository;
    private final Clock clock;

    public Page<WasteRecordResponse> waste(
            Long storeId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        DateRange range = optionalRange(startDate, endDate);
        Pageable boundedPageable = boundedPageable(
                pageable,
                WASTE_SORT_FIELDS,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return wasteRecordRepository.findReportPage(storeId, range.start(), range.endExclusive(), boundedPageable)
                .map(this::toWasteResponse);
    }

    public WasteReportSummaryResponse wasteSummary(Long storeId, LocalDate startDate, LocalDate endDate) {
        DateRange range = explicitRange(startDate, endDate);
        WasteRecordRepository.WasteTotals totals = wasteRecordRepository.aggregateTotals(
                storeId,
                range.start(),
                range.endExclusive()
        );
        List<WasteReportSummaryResponse.ReasonBreakdown> reasonBreakdown = wasteRecordRepository
                .aggregateByReason(storeId, range.start(), range.endExclusive())
                .stream()
                .map(row -> new WasteReportSummaryResponse.ReasonBreakdown(
                        row.getReason(),
                        zero(row.getEstimatedCost()),
                        zero(row.getQuantity()),
                        zero(row.getRecordCount())
                ))
                .toList();
        return new WasteReportSummaryResponse(
                startDate,
                endDate,
                totalCost(totals),
                totalQuantity(totals),
                recordCount(totals),
                affectedIngredientCount(totals),
                reasonBreakdown
        );
    }

    public ResponseEntity<StreamingResponseBody> exportWaste(
            Long storeId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        DateRange range = explicitRange(startDate, endDate);
        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
            )) {
                writer.write('\uFEFF');
                CsvEscaper.appendRow(writer, "createdAt", "store", "ingredient", "batch", "quantity", "unit",
                        "reason", "estimatedCost", "recordedBy");
                Instant cursorCreatedAt = range.endExclusive();
                long cursorId = Long.MAX_VALUE;
                while (true) {
                    List<WasteRecordRepository.WasteExportRow> rows = wasteRecordRepository.findExportRows(
                            storeId,
                            range.start(),
                            range.endExclusive(),
                            cursorCreatedAt,
                            cursorId,
                            PageRequest.of(0, EXPORT_CHUNK_SIZE)
                    );
                    if (rows.isEmpty()) break;
                    for (WasteRecordRepository.WasteExportRow row : rows) {
                        CsvEscaper.appendRow(writer,
                                row.getCreatedAt(), row.getStoreName(), row.getIngredientName(), row.getBatchNumber(),
                                row.getQuantity(), row.getUnit(), row.getReason(), row.getEstimatedCost(),
                                row.getRecordedBy());
                    }
                    WasteRecordRepository.WasteExportRow last = rows.get(rows.size() - 1);
                    cursorCreatedAt = last.getCreatedAt();
                    cursorId = last.getId();
                    writer.flush();
                }
            }
        };
        return csv("waste-report.csv", body);
    }

    public ResponseEntity<StreamingResponseBody> exportInventory(
            Long storeId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        DateRange range = explicitRange(startDate, endDate);
        StreamingResponseBody body = outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
            )) {
                writer.write('\uFEFF');
                CsvEscaper.appendRow(writer, "createdAt", "store", "ingredient", "batch", "type", "reason",
                        "quantity", "unit", "unitCost", "recordedBy");
                Instant cursorCreatedAt = range.endExclusive();
                long cursorId = Long.MAX_VALUE;
                while (true) {
                    List<StockTransactionRepository.StockExportRow> rows = transactionRepository.findExportRows(
                            storeId,
                            range.start(),
                            range.endExclusive(),
                            cursorCreatedAt,
                            cursorId,
                            PageRequest.of(0, EXPORT_CHUNK_SIZE)
                    );
                    if (rows.isEmpty()) break;
                    for (StockTransactionRepository.StockExportRow row : rows) {
                        CsvEscaper.appendRow(writer,
                                row.getCreatedAt(), row.getStoreName(), row.getIngredientName(), row.getBatchNumber(),
                                row.getType(), row.getReason(), row.getQuantity(), row.getUnit(), row.getUnitCost(),
                                row.getRecordedBy());
                    }
                    StockTransactionRepository.StockExportRow last = rows.get(rows.size() - 1);
                    cursorCreatedAt = last.getCreatedAt();
                    cursorId = last.getId();
                    writer.flush();
                }
            }
        };
        return csv("inventory-transactions.csv", body);
    }

    public WasteDashboardResponse wasteDashboard(Long storeId, String period) {
        String normalizedPeriod = period.toLowerCase(Locale.ROOT);
        if (!normalizedPeriod.equals("week") && !normalizedPeriod.equals("month")) {
            throw validationError("period must be either 'week' or 'month'");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate currentStart = normalizedPeriod.equals("week") ? today.minusDays(6) : today.withDayOfMonth(1);
        LocalDate currentEndExclusive = today.plusDays(1);
        LocalDate previousStart = normalizedPeriod.equals("week")
                ? currentStart.minusDays(7)
                : currentStart.minusMonths(1);
        long currentPeriodDays = ChronoUnit.DAYS.between(currentStart, currentEndExclusive);
        LocalDate previousEndExclusive = previousStart.plusDays(currentPeriodDays);
        if (previousEndExclusive.isAfter(currentStart)) previousEndExclusive = currentStart;

        WasteRecordRepository.WasteTotals current = wasteRecordRepository.aggregateTotals(
                storeId, atStartOfDay(currentStart), atStartOfDay(currentEndExclusive)
        );
        WasteRecordRepository.WasteTotals previous = wasteRecordRepository.aggregateTotals(
                storeId, atStartOfDay(previousStart), atStartOfDay(previousEndExclusive)
        );
        BigDecimal currentCost = totalCost(current);
        BigDecimal previousCost = totalCost(previous);
        BigDecimal change = previousCost.signum() == 0
                ? BigDecimal.ZERO
                : currentCost.subtract(previousCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousCost, 2, RoundingMode.HALF_UP);

        List<WasteDashboardResponse.TopWasteIngredient> top = wasteRecordRepository.aggregateByIngredient(
                        storeId,
                        atStartOfDay(currentStart),
                        atStartOfDay(currentEndExclusive),
                        PageRequest.of(0, 5)
                ).stream()
                .map(row -> new WasteDashboardResponse.TopWasteIngredient(
                        row.getIngredientId(),
                        row.getIngredientName(),
                        row.getUnit(),
                        zero(row.getQuantity()),
                        zero(row.getEstimatedCost())
                ))
                .toList();
        return new WasteDashboardResponse(normalizedPeriod, currentCost, previousCost, change, top);
    }

    public Page<AuditLogResponse> auditLog(
            Long storeId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        DateRange range = optionalRange(startDate, endDate);
        Pageable boundedPageable = boundedPageable(
                pageable,
                TRANSACTION_SORT_FIELDS,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return transactionRepository.findAuditPage(storeId, range.start(), range.endExclusive(), boundedPageable)
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

    public Page<StockTransactionResponse> transactions(Long storeId, Pageable pageable) {
        Pageable boundedPageable = boundedPageable(
                pageable,
                TRANSACTION_SORT_FIELDS,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return transactionRepository.findByStoreId(storeId, boundedPageable)
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

    private WasteRecordResponse toWasteResponse(WasteRecord wasteRecord) {
        return new WasteRecordResponse(
                wasteRecord.getId(),
                wasteRecord.getStore().getId(),
                wasteRecord.getIngredient().getId(),
                wasteRecord.getIngredient().getName(),
                wasteRecord.getIngredient().getUnit(),
                wasteRecord.getBatch() == null ? null : wasteRecord.getBatch().getId(),
                wasteRecord.getQuantity(),
                wasteRecord.getReason(),
                wasteRecord.getEstimatedCost(),
                wasteRecord.getCreatedBy() == null ? null : wasteRecord.getCreatedBy().getEmail(),
                wasteRecord.getCreatedAt()
        );
    }

    private Pageable boundedPageable(Pageable requested, Set<String> allowedProperties, Sort defaultSort) {
        int page = requested.isPaged() ? Math.max(requested.getPageNumber(), 0) : 0;
        int size = requested.isPaged() ? Math.min(Math.max(requested.getPageSize(), 1), MAX_PAGE_SIZE) : 20;
        List<Sort.Order> orders = new ArrayList<>();
        requested.getSort().forEach(order -> {
            if (allowedProperties.contains(order.getProperty())) orders.add(order);
        });
        if (orders.isEmpty()) {
            defaultSort.forEach(orders::add);
        } else if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
            orders.add(Sort.Order.desc("id"));
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }

    private DateRange optionalRange(LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate endDate = requestedEnd == null ? LocalDate.now(clock) : requestedEnd;
        LocalDate startDate = requestedStart == null ? endDate.minusDays(29) : requestedStart;
        return explicitRange(startDate, endDate);
    }

    private DateRange explicitRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw validationError("startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw validationError("startDate must be on or before endDate");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays > MAX_REPORT_RANGE_DAYS) {
            throw validationError("Report range cannot exceed " + MAX_REPORT_RANGE_DAYS + " days");
        }
        return new DateRange(atStartOfDay(startDate), atStartOfDay(endDate.plusDays(1)));
    }

    private Instant atStartOfDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    private BigDecimal totalCost(WasteRecordRepository.WasteTotals totals) {
        return totals == null ? BigDecimal.ZERO : zero(totals.getTotalCost());
    }

    private BigDecimal totalQuantity(WasteRecordRepository.WasteTotals totals) {
        return totals == null ? BigDecimal.ZERO : zero(totals.getTotalQuantity());
    }

    private long recordCount(WasteRecordRepository.WasteTotals totals) {
        return totals == null ? 0 : zero(totals.getRecordCount());
    }

    private long affectedIngredientCount(WasteRecordRepository.WasteTotals totals) {
        return totals == null ? 0 : zero(totals.getAffectedIngredientCount());
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long zero(Long value) {
        return value == null ? 0 : value;
    }

    private AppException validationError(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        MediaType csvMediaType = new MediaType("text", "csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(csvMediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private record DateRange(Instant start, Instant endExclusive) {
    }
}
