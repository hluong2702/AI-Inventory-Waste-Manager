package vn.inventoryai.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.inventoryai.common.security.TenantContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryControllerTest {
    private static final long STORE_ID = 25L;

    private InventoryBatchRepository batchRepository;
    private InventoryController controller;

    @BeforeEach
    void setUp() {
        batchRepository = mock(InventoryBatchRepository.class);
        controller = new InventoryController(mock(InventoryService.class), batchRepository, Clock.systemUTC());
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void inventorySummaryComesFromTenantScopedAggregateAndCapsPageSize() {
        InventoryBatchRepository.InventorySummaryRow row = mock(InventoryBatchRepository.InventorySummaryRow.class);
        when(row.getIngredientId()).thenReturn(9L);
        when(row.getCode()).thenReturn("MILK");
        when(row.getName()).thenReturn("Sữa");
        when(row.getUnit()).thenReturn("lít");
        when(row.getCategory()).thenReturn("Sữa");
        when(row.getMinStock()).thenReturn(new BigDecimal("5"));
        when(row.getMaxStock()).thenReturn(new BigDecimal("30"));
        when(row.getTotalQuantity()).thenReturn(new BigDecimal("14"));
        when(row.getSellableQuantity()).thenReturn(new BigDecimal("10"));
        when(row.getActiveBatchesCount()).thenReturn(3L);
        when(row.getExpiredBatchesCount()).thenReturn(1L);
        when(row.getExpiringSoonBatchesCount()).thenReturn(1L);
        when(batchRepository.summarizeInventory(eq(STORE_ID), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(row),
                        invocation.getArgument(3),
                        1
                ));

        var response = controller.summary(PageRequest.of(0, 5_000));

        assertThat(response.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.ingredientId()).isEqualTo(9L);
            assertThat(summary.totalQuantity()).isEqualByComparingTo("14");
            assertThat(summary.sellableQuantity()).isEqualByComparingTo("10");
            assertThat(summary.expiredBatchesCount()).isEqualTo(1);
        });
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(batchRepository).summarizeInventory(eq(STORE_ID), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void ingredientBatchListIsTenantScopedActiveOnlyAndUsesSafeDeterministicSort() {
        when(batchRepository.findByStoreIdAndIngredientIdAndQuantityGreaterThan(
                eq(STORE_ID),
                eq(9L),
                eq(BigDecimal.ZERO),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        controller.batches(
                9L,
                PageRequest.of(0, 500, Sort.by("ingredient.store.owner.passwordHash"))
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(batchRepository).findByStoreIdAndIngredientIdAndQuantityGreaterThan(
                eq(STORE_ID),
                eq(9L),
                eq(BigDecimal.ZERO),
                pageableCaptor.capture()
        );
        Pageable bounded = pageableCaptor.getValue();
        assertThat(bounded.getPageSize()).isEqualTo(100);
        assertThat(bounded.getSort().getOrderFor("expiryDate")).isNotNull();
        assertThat(bounded.getSort().getOrderFor("id")).isNotNull();
        assertThat(bounded.getSort().getOrderFor("ingredient.store.owner.passwordHash")).isNull();
    }
}
