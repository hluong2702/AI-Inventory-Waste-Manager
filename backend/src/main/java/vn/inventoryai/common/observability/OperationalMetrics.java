package vn.inventoryai.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    private final MeterRegistry meterRegistry;

    public OperationalMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void paymentReconciliation(String phase, String outcome) {
        counter("inventoryai.payment.reconciliation", "phase", phase, "outcome", outcome).increment();
    }

    public void invitationEmail(String outcome) {
        counter("inventoryai.invitation.email", "outcome", outcome).increment();
    }

    public void alertGeneration(String outcome) {
        counter("inventoryai.alert.generation", "outcome", outcome).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }
}
