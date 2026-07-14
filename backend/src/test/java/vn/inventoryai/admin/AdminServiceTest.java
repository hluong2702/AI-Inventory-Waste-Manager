package vn.inventoryai.admin;

import org.junit.jupiter.api.Test;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.store.SubscriptionRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminServiceTest {
    @Test
    void dashboardLoadsTransactionCountsWithOneGroupedQuery() {
        StoreRepository stores = mock(StoreRepository.class);
        UserRepository users = mock(UserRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        StockTransactionRepository transactions = mock(StockTransactionRepository.class);
        IngredientRepository ingredients = mock(IngredientRepository.class);
        AdminService service = new AdminService(stores, users, subscriptions, transactions, ingredients);

        Store first = store(1L, "Một");
        Store second = store(2L, "Hai");
        when(stores.findAll()).thenReturn(List.of(first, second));
        when(subscriptions.findAll()).thenReturn(List.of());
        when(transactions.countGroupedByStoreId()).thenReturn(List.<Object[]>of(new Object[]{1L, 7L}));

        var dashboard = service.dashboard();

        assertThat(dashboard.mostActiveStores()).extracting(activity -> activity.transactionCount()).containsExactly(7L, 0L);
        verify(transactions, times(1)).countGroupedByStoreId();
        verify(transactions, never()).countByStoreId(anyLong());
    }

    private Store store(Long id, String name) {
        Store store = new Store();
        store.setId(id);
        store.setName(name);
        return store;
    }
}
