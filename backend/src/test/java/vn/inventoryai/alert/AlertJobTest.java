package vn.inventoryai.alert;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.observability.OperationalMetrics;
import vn.inventoryai.store.StoreRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertJobTest {
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-07-14");

    @Test
    void scansActiveStoresWithBoundedKeysetPages() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        AlertGenerationService generationService = mock(AlertGenerationService.class);
        AlertJob job = job(storeRepository, generationService, 3, 2);
        when(storeRepository.findIdsByStatusAfter(eq(StoreStatus.ACTIVE), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(10L, 20L));
        when(storeRepository.findIdsByStatusAfter(eq(StoreStatus.ACTIVE), eq(20L), any(Pageable.class)))
                .thenReturn(List.of(30L));

        job.generateDailyAlerts();

        LocalDate threshold = LocalDate.parse("2026-07-17");
        verify(generationService).generateForStore(10L, BUSINESS_DATE, threshold);
        verify(generationService).generateForStore(20L, BUSINESS_DATE, threshold);
        verify(generationService).generateForStore(30L, BUSINESS_DATE, threshold);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(storeRepository).findIdsByStatusAfter(eq(StoreStatus.ACTIVE), eq(0L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void aFailedTenantDoesNotRollbackOrSkipTheNextTenant() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        AlertGenerationService generationService = mock(AlertGenerationService.class);
        AlertJob job = job(storeRepository, generationService, 3, 100);
        when(storeRepository.findIdsByStatusAfter(eq(StoreStatus.ACTIVE), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(10L, 20L));
        doThrow(new IllegalStateException("database unavailable"))
                .when(generationService)
                .generateForStore(10L, BUSINESS_DATE, LocalDate.parse("2026-07-17"));

        job.generateDailyAlerts();

        verify(generationService).generateForStore(
                20L,
                BUSINESS_DATE,
                LocalDate.parse("2026-07-17")
        );
    }

    @Test
    void unsafeConfigurationValuesAreClamped() {
        StoreRepository storeRepository = mock(StoreRepository.class);
        AlertGenerationService generationService = mock(AlertGenerationService.class);
        AlertJob job = job(storeRepository, generationService, -10, 5_000);
        when(storeRepository.findIdsByStatusAfter(eq(StoreStatus.ACTIVE), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(44L));

        job.generateDailyAlerts();

        verify(generationService).generateForStore(44L, BUSINESS_DATE, BUSINESS_DATE);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(storeRepository).findIdsByStatusAfter(eq(StoreStatus.ACTIVE), eq(0L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(500);
    }

    private AlertJob job(
            StoreRepository storeRepository,
            AlertGenerationService generationService,
            long expiringDays,
            int pageSize
    ) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T08:00:00Z"), ZoneOffset.UTC);
        AlertJob job = new AlertJob(storeRepository, generationService, clock, mock(OperationalMetrics.class));
        ReflectionTestUtils.setField(job, "expiringDays", expiringDays);
        ReflectionTestUtils.setField(job, "configuredStorePageSize", pageSize);
        ReflectionTestUtils.setField(job, "alertZone", "UTC");
        return job;
    }
}
