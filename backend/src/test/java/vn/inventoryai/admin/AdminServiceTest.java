package vn.inventoryai.admin;

import org.junit.jupiter.api.Test;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.inventory.WasteRecordRepository;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminServiceTest {
    @Test
    void dashboardUsesBoundedAggregateQueriesInsteadOfLoadingEveryTenant() {
        StoreRepository stores = mock(StoreRepository.class);
        UserRepository users = mock(UserRepository.class);
        TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
        TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
        StockTransactionRepository transactions = mock(StockTransactionRepository.class);
        IngredientRepository ingredients = mock(IngredientRepository.class);
        WasteRecordRepository waste = mock(WasteRecordRepository.class);
        AdminService service = new AdminService(
                stores,
                users,
                memberships,
                subscriptions,
                transactions,
                ingredients,
                waste,
                Clock.systemUTC()
        );

        StockTransactionRepository.StoreActivity activity = mock(StockTransactionRepository.StoreActivity.class);
        when(activity.getStoreId()).thenReturn(1L);
        when(activity.getStoreName()).thenReturn("Một");
        when(activity.getTransactionCount()).thenReturn(7L);
        when(subscriptions.sumActiveMonthlyRecurringRevenue()).thenReturn(new BigDecimal("998000"));
        when(transactions.findMostActiveStores(any())).thenReturn(List.of(activity));

        var dashboard = service.dashboard();

        assertThat(dashboard.mrr()).isEqualByComparingTo("998000");
        assertThat(dashboard.mostActiveStores()).extracting(item -> item.transactionCount()).containsExactly(7L);
        verify(transactions, times(1)).findMostActiveStores(argThat(pageable -> pageable.getPageSize() == 10));
        verify(transactions, never()).countByStoreId(anyLong());
        verify(stores, never()).findAll();
        verify(subscriptions, never()).findAll();
    }

}
