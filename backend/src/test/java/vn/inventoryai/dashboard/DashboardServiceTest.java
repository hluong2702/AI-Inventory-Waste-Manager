package vn.inventoryai.dashboard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.inventoryai.alert.AlertRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.insight.InventoryInsightService;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.WasteRecordRepository;
import vn.inventoryai.subscription.SubscriptionService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    private static final long STORE_ID = 10L;

    @Mock IngredientRepository ingredientRepository;
    @Mock InventoryBatchRepository batchRepository;
    @Mock WasteRecordRepository wasteRecordRepository;
    @Mock AlertRepository alertRepository;
    @Mock InventoryInsightService insightService;
    @Mock SubscriptionService subscriptionService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-14T05:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh")
        );
        service = new DashboardService(
                ingredientRepository,
                batchRepository,
                wasteRecordRepository,
                alertRepository,
                insightService,
                subscriptionService,
                clock
        );
        when(batchRepository
                .findTop5ByStoreIdAndQuantityGreaterThanAndExpiryDateLessThanEqualOrderByExpiryDateAscReceivedAtAscIdAsc(
                        eq(STORE_ID),
                        eq(BigDecimal.ZERO),
                        any()
                )).thenReturn(List.of());
        when(alertRepository.findTop3ByStoreIdAndResolvedFalseOrderByCreatedAtDesc(STORE_ID))
                .thenReturn(List.of());
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffWithoutAdvancedFeatureNeverReceivesFakeZeroFinancialOrInsightData() {
        authenticate(Role.STAFF);
        when(subscriptionService.hasFeature(STORE_ID, "ADVANCED_FORECAST")).thenReturn(false);
        when(ingredientRepository.countByStoreIdAndDeletedFalse(STORE_ID)).thenReturn(12L);
        when(alertRepository.countByStoreIdAndResolvedFalse(STORE_ID)).thenReturn(2L);

        var response = service.current();

        assertThat(response.reportsAvailable()).isFalse();
        assertThat(response.waste()).isNull();
        assertThat(response.insightsAvailable()).isFalse();
        assertThat(response.insights()).isNull();
        assertThat(response.canResolveAlerts()).isFalse();
        assertThat(response.ingredientCount()).isEqualTo(12L);
        assertThat(response.openAlertCount()).isEqualTo(2L);
        verifyNoInteractions(wasteRecordRepository, insightService);
    }

    @Test
    void ownerReceivesServerAggregatesAndGlobalInsightCounts() {
        authenticate(Role.OWNER);
        when(subscriptionService.hasFeature(STORE_ID, "ADVANCED_FORECAST")).thenReturn(true);
        WasteRecordRepository.WasteTotals currentTotals = totals("150000");
        WasteRecordRepository.WasteTotals previousTotals = totals("100000");
        when(wasteRecordRepository.aggregateTotals(eq(STORE_ID), any(), any()))
                .thenReturn(currentTotals, previousTotals);
        when(wasteRecordRepository.aggregateByIngredient(eq(STORE_ID), any(), any(), any()))
                .thenReturn(List.of());
        when(wasteRecordRepository.aggregateDaily(eq(STORE_ID), any(), any(), any()))
                .thenReturn(List.of());
        when(insightService.dashboardSummary(STORE_ID, 4))
                .thenReturn(new InventoryInsightService.DashboardInsightSummary(500, 3, 8, 489, List.of()));

        var response = service.current();

        assertThat(response.reportsAvailable()).isTrue();
        assertThat(response.waste()).isNotNull();
        assertThat(response.waste().currentWasteCost()).isEqualByComparingTo("150000");
        assertThat(response.waste().changePercent()).isEqualByComparingTo("50.00");
        assertThat(response.insightsAvailable()).isTrue();
        assertThat(response.insights().totalCount()).isEqualTo(500);
        assertThat(response.insights().highRiskCount()).isEqualTo(3);
    }

    private WasteRecordRepository.WasteTotals totals(String cost) {
        WasteRecordRepository.WasteTotals totals = mock(WasteRecordRepository.WasteTotals.class);
        when(totals.getTotalCost()).thenReturn(new BigDecimal(cost));
        return totals;
    }

    private void authenticate(Role role) {
        UserPrincipal principal = new UserPrincipal(1L, STORE_ID, "user@example.com", role, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
