package vn.inventoryai.alert;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRepositoryQueryContractTest {

    @Test
    void createQueriesAreTenantScopedActiveStoreOnlyAndDatabaseIdempotent() throws NoSuchMethodException {
        String lowStockSql = query("createMissingLowStock", Long.class, LocalDate.class);
        String expirySql = query("createMissingExpiryRisk", Long.class, LocalDate.class);

        assertThat(lowStockSql)
                .contains("ingredient.store_id = :storeId")
                .contains("store.status = 'ACTIVE'")
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(expirySql)
                .contains("batch.store_id = :storeId")
                .contains("ingredient.store_id = batch.store_id")
                .contains("store.status = 'ACTIVE'")
                .contains("ON DUPLICATE KEY UPDATE");
    }

    @Test
    void resolveQueriesCannotAffectAnotherTenant() throws NoSuchMethodException {
        String lowStockSql = query("resolveRecoveredLowStock", Long.class, LocalDate.class);
        String expirySql = query("resolveClearedExpiryRisk", Long.class, LocalDate.class);

        assertThat(lowStockSql)
                .contains("alert_row.store_id = :storeId")
                .contains("ingredient.store_id = alert_row.store_id");
        assertThat(expirySql)
                .contains("alert_row.store_id = :storeId")
                .contains("ingredient.store_id = alert_row.store_id")
                .contains("batch.store_id = alert_row.store_id");
    }

    private String query(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = AlertRepository.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Query.class).value();
    }
}
