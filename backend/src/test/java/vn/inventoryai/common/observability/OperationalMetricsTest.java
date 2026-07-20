package vn.inventoryai.common.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalMetricsTest {
    @Test
    void recordsOperationalOutcomesWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);

        metrics.paymentReconciliation("creation", "success");
        metrics.invitationEmail("delivery_failure");
        metrics.alertGeneration("failure");

        assertThat(registry.get("inventoryai.payment.reconciliation")
                .tags("phase", "creation", "outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("inventoryai.invitation.email")
                .tag("outcome", "delivery_failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("inventoryai.alert.generation")
                .tag("outcome", "failure").counter().count()).isEqualTo(1);
    }
}
