package vn.inventoryai.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.inventory.WasteRecordRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private static final long STORE_ID = 17L;

    private StockTransactionRepository transactionRepository;
    private WasteRecordRepository wasteRecordRepository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(StockTransactionRepository.class);
        wasteRecordRepository = mock(WasteRecordRepository.class);
        service = new ReportService(transactionRepository, wasteRecordRepository, Clock.systemUTC());
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void wasteSummaryUsesTenantScopedDatabaseAggregates() {
        LocalDate startDate = LocalDate.parse("2026-07-01");
        LocalDate endDate = LocalDate.parse("2026-07-14");
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant endExclusive = Instant.parse("2026-07-15T00:00:00Z");
        WasteRecordRepository.WasteTotals totals = mock(WasteRecordRepository.WasteTotals.class);
        when(totals.getTotalCost()).thenReturn(new BigDecimal("125000"));
        when(totals.getTotalQuantity()).thenReturn(new BigDecimal("12.5"));
        when(totals.getRecordCount()).thenReturn(42L);
        when(totals.getAffectedIngredientCount()).thenReturn(6L);
        WasteRecordRepository.WasteReasonAggregate reason = mock(WasteRecordRepository.WasteReasonAggregate.class);
        when(reason.getReason()).thenReturn("EXPIRED");
        when(reason.getEstimatedCost()).thenReturn(new BigDecimal("90000"));
        when(reason.getQuantity()).thenReturn(new BigDecimal("8"));
        when(reason.getRecordCount()).thenReturn(30L);
        when(wasteRecordRepository.aggregateTotals(STORE_ID, start, endExclusive)).thenReturn(totals);
        when(wasteRecordRepository.aggregateByReason(STORE_ID, start, endExclusive)).thenReturn(List.of(reason));

        var response = service.wasteSummary(STORE_ID, startDate, endDate);

        assertThat(response.totalWasteCost()).isEqualByComparingTo("125000");
        assertThat(response.totalQuantity()).isEqualByComparingTo("12.5");
        assertThat(response.recordCount()).isEqualTo(42);
        assertThat(response.affectedIngredientCount()).isEqualTo(6);
        assertThat(response.reasonBreakdown()).singleElement().satisfies(item -> {
            assertThat(item.reason()).isEqualTo("EXPIRED");
            assertThat(item.estimatedCost()).isEqualByComparingTo("90000");
            assertThat(item.recordCount()).isEqualTo(30);
        });
    }

    @Test
    void reportDayBoundariesUseConfiguredBusinessTimeZone() {
        Clock hoChiMinhClock = Clock.fixed(
                Instant.parse("2026-07-14T18:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh")
        );
        ReportService localTimeService = new ReportService(
                transactionRepository,
                wasteRecordRepository,
                hoChiMinhClock
        );
        when(wasteRecordRepository.aggregateByReason(any(), any(), any())).thenReturn(List.of());

        localTimeService.wasteSummary(
                STORE_ID,
                LocalDate.parse("2026-07-15"),
                LocalDate.parse("2026-07-15")
        );

        verify(wasteRecordRepository).aggregateTotals(
                STORE_ID,
                Instant.parse("2026-07-14T17:00:00Z"),
                Instant.parse("2026-07-15T17:00:00Z")
        );
    }

    @Test
    void explicitReportRangeRejectsInvalidAndOversizedRanges() {
        assertThatThrownBy(() -> service.wasteSummary(
                STORE_ID,
                LocalDate.parse("2026-07-15"),
                LocalDate.parse("2026-07-14")
        )).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(exception.getStatus().value()).isEqualTo(400);
        });

        assertThatThrownBy(() -> service.exportInventory(
                STORE_ID,
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2026-07-14")
        )).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void wasteListCapsPageSizeAndReplacesUnsupportedSort() {
        when(wasteRecordRepository.findReportPage(eq(STORE_ID), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.waste(
                STORE_ID,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                PageRequest.of(0, 1_000, Sort.by("store.owner.passwordHash"))
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(wasteRecordRepository).findReportPage(eq(STORE_ID), any(), any(), pageableCaptor.capture());
        Pageable bounded = pageableCaptor.getValue();
        assertThat(bounded.getPageSize()).isEqualTo(ReportService.MAX_PAGE_SIZE);
        assertThat(bounded.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(bounded.getSort().getOrderFor("id")).isNotNull();
        assertThat(bounded.getSort().getOrderFor("store.owner.passwordHash")).isNull();
    }

    @Test
    void wasteExportStreamsBoundedProjectionChunks() throws Exception {
        WasteRecordRepository.WasteExportRow row = mock(WasteRecordRepository.WasteExportRow.class);
        when(row.getId()).thenReturn(81L);
        when(row.getCreatedAt()).thenReturn(Instant.parse("2026-07-14T10:00:00Z"));
        when(row.getStoreName()).thenReturn("Bếp, Quận 1");
        when(row.getIngredientName()).thenReturn("Sữa");
        when(row.getBatchNumber()).thenReturn("LOT-01");
        when(row.getQuantity()).thenReturn(new BigDecimal("2.5"));
        when(row.getUnit()).thenReturn("lít");
        when(row.getReason()).thenReturn("EXPIRED");
        when(row.getEstimatedCost()).thenReturn(new BigDecimal("50000"));
        when(row.getRecordedBy()).thenReturn("owner@example.com");
        when(wasteRecordRepository.findExportRows(
                eq(STORE_ID), any(), any(), any(), any(), any(Pageable.class)
        )).thenReturn(List.of(row)).thenReturn(List.<WasteRecordRepository.WasteExportRow>of());

        var response = service.exportWaste(
                STORE_ID,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14")
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        String csv = output.toString(StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFcreatedAt,store,ingredient");
        assertThat(csv).contains("\"Bếp, Quận 1\"");
        assertThat(csv).contains("Sữa,LOT-01,2.5,lít,EXPIRED,50000,owner@example.com");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(wasteRecordRepository, org.mockito.Mockito.times(2)).findExportRows(
                eq(STORE_ID), any(), any(), any(), any(), pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(ReportService.EXPORT_CHUNK_SIZE);
    }
}
