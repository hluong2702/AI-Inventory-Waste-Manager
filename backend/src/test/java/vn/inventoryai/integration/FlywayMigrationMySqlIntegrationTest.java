package vn.inventoryai.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationMySqlIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("inventory_ai")
            .withUsername("inventory")
            .withPassword("inventory");

    @Test
    void allMigrationsApplyOnFreshMySqlAndRemoveLegacySubscriptionTable() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
        try (Connection connection = MYSQL.createConnection("");
             ResultSet tables = connection.getMetaData().getTables(
                     MYSQL.getDatabaseName(), null, "%", new String[]{"TABLE"}
             )) {
            boolean tenantSubscriptionsFound = false;
            boolean legacySubscriptionsFound = false;
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tenantSubscriptionsFound |= "tenant_subscriptions".equalsIgnoreCase(tableName);
                legacySubscriptionsFound |= "subscriptions".equalsIgnoreCase(tableName);
            }
            assertThat(tenantSubscriptionsFound).isTrue();
            assertThat(legacySubscriptionsFound).isFalse();
        }
    }
}
