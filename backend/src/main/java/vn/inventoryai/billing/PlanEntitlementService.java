package vn.inventoryai.billing;

import org.springframework.stereotype.Service;
import vn.inventoryai.common.enums.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PlanEntitlementService {
    private final Map<SubscriptionPlan, PlanDefinition> definitions = new EnumMap<>(SubscriptionPlan.class);

    public PlanEntitlementService() {
        definitions.put(SubscriptionPlan.FREE, new PlanDefinition(
                SubscriptionPlan.FREE,
                BigDecimal.ZERO,
                new PlanLimits(1, 2, 30),
                List.of("BASIC_ALERTS", "BASIC_REPORTS")
        ));
        definitions.put(SubscriptionPlan.BASIC, new PlanDefinition(
                SubscriptionPlan.BASIC,
                BigDecimal.valueOf(299_000),
                new PlanLimits(1, 10, 500),
                List.of("BASIC_ALERTS", "BASIC_REPORTS", "BASIC_FORECAST")
        ));
        definitions.put(SubscriptionPlan.PRO, new PlanDefinition(
                SubscriptionPlan.PRO,
                BigDecimal.valueOf(699_000),
                new PlanLimits(null, null, null),
                List.of("BASIC_ALERTS", "BASIC_REPORTS", "BASIC_FORECAST", "ADVANCED_FORECAST", "EXPORT_REPORTS", "MULTI_STORE")
        ));
    }

    public PlanDefinition definition(SubscriptionPlan plan) {
        return definitions.get(plan);
    }

    public PlanLimits limits(SubscriptionPlan plan) {
        return definition(plan).limits();
    }

    public List<PlanDefinition> allPlans() {
        return List.copyOf(definitions.values());
    }

    public int maxStaff(SubscriptionPlan plan) {
        Integer value = limits(plan).staff();
        return value == null ? Integer.MAX_VALUE : value;
    }

    public int maxIngredients(SubscriptionPlan plan) {
        Integer value = limits(plan).ingredients();
        return value == null ? Integer.MAX_VALUE : value;
    }
}
