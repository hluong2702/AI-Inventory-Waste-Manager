package vn.inventoryai.forecast;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.inventory.Ingredient;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.InventoryBatchRepository;
import vn.inventoryai.inventory.StockTransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForecastServiceTest {
    private static final Long STORE_ID = 99L;
    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");

    private IngredientRepository ingredientRepository;
    private InventoryBatchRepository batchRepository;
    private StockTransactionRepository transactionRepository;
    private ForecastService service;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        batchRepository = mock(InventoryBatchRepository.class);
        transactionRepository = mock(StockTransactionRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new ForecastService(ingredientRepository, batchRepository, transactionRepository, clock);

        ingredient = new Ingredient();
        ingredient.setId(1L);
        ingredient.setCode("MILK-FRESH");
        ingredient.setName("Sữa tươi");
        ingredient.setUnit("lít");
        ingredient.setMinStock(new BigDecimal("3"));

        UserPrincipal principal = new UserPrincipal(7L, STORE_ID, "manager@example.com", Role.MANAGER, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void forecastUsesOnlyConsumptionReasonAndSellableStock() {
        when(ingredientRepository.findByIdAndStoreIdAndDeletedFalse(ingredient.getId(), STORE_ID))
                .thenReturn(Optional.of(ingredient));
        when(transactionRepository.sumQuantitySinceByReason(
                eq(STORE_ID),
                eq(ingredient.getId()),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                any(Instant.class)
        )).thenReturn(new BigDecimal("14"));
        when(batchRepository.sumSellable(STORE_ID, ingredient.getId(), LocalDate.parse("2026-07-14")))
                .thenReturn(new BigDecimal("5"));

        var result = service.forecast(ingredient.getId(), 7);

        assertThat(result.ingredientCode()).isEqualTo("MILK-FRESH");
        assertThat(result.avgDailyUsage()).isEqualByComparingTo("2");
        assertThat(result.currentStock()).isEqualByComparingTo("5");
        assertThat(result.recommendedOrder()).isEqualByComparingTo("12");
        verify(transactionRepository).sumQuantitySinceByReason(
                STORE_ID,
                ingredient.getId(),
                StockTransactionType.OUT,
                "EXPORT_CONSUME",
                NOW.minusSeconds(7L * 24 * 60 * 60)
        );
    }

    @Test
    void forecastAllUsesOneTenantScopedAggregateCallForWholeBoundedPage() {
        Ingredient second = ingredient(2L, "COFFEE", "Cà phê");
        Ingredient third = ingredient(3L, "PEARL", "Trân châu");
        when(ingredientRepository.findByStoreIdAndDeletedFalse(eq(STORE_ID), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(ingredient, second, third),
                        invocation.getArgument(1),
                        3
                ));
        when(transactionRepository.sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID),
                argThat(ids -> ids.equals(List.of(1L, 2L, 3L))),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                any(Instant.class)
        )).thenReturn(List.of());
        when(batchRepository.sumSellableGroupedByIngredientIds(
                eq(STORE_ID),
                argThat(ids -> ids.equals(List.of(1L, 2L, 3L))),
                eq(LocalDate.parse("2026-07-14"))
        )).thenReturn(List.of());

        var results = service.forecastAll(7, PageRequest.of(0, 500));

        assertThat(results.getContent()).hasSize(3).allSatisfy(result -> {
            assertThat(result.currentStock()).isEqualByComparingTo("0");
            assertThat(result.avgDailyUsage()).isEqualByComparingTo("0");
            assertThat(result.recommendedOrder()).isEqualByComparingTo("3");
        });
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ingredientRepository).findByStoreIdAndDeletedFalse(eq(STORE_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(ForecastService.MAX_PAGE_SIZE);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("name")).isNotNull();
        verify(transactionRepository, times(1)).sumQuantitySinceGroupedByIngredientIds(
                eq(STORE_ID),
                any(),
                eq(StockTransactionType.OUT),
                eq("EXPORT_CONSUME"),
                eq(NOW.minusSeconds(7L * 24 * 60 * 60))
        );
        verify(batchRepository, times(1)).sumSellableGroupedByIngredientIds(
                eq(STORE_ID),
                any(),
                eq(LocalDate.parse("2026-07-14"))
        );
    }

    @Test
    void forecastAllSkipsAggregateQueriesForEmptyPage() {
        when(ingredientRepository.findByStoreIdAndDeletedFalse(eq(STORE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(4, 25), 0));

        var results = service.forecastAll(7, PageRequest.of(4, 25));

        assertThat(results).isEmpty();
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(batchRepository);
    }

    private Ingredient ingredient(Long id, String code, String name) {
        Ingredient result = new Ingredient();
        result.setId(id);
        result.setCode(code);
        result.setName(name);
        result.setUnit("kg");
        result.setMinStock(new BigDecimal("3"));
        return result;
    }
}
