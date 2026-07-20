package vn.inventoryai.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.subscription.SubscriptionPlanEntity;
import vn.inventoryai.subscription.SubscriptionPlanRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanEntitlementService {
    private final SubscriptionPlanRepository planRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlanDefinition definition(SubscriptionPlan plan) {
        SubscriptionPlanEntity entity = planRepository.findByCodeAndActiveTrue(plan)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Active plan definition not found"
                ));
        return toDefinition(entity);
    }

    public PlanLimits limits(SubscriptionPlan plan) {
        return definition(plan).limits();
    }

    @Transactional(readOnly = true)
    public List<PlanDefinition> allPlans() {
        return planRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(this::toDefinition)
                .toList();
    }

    public int maxStaff(SubscriptionPlan plan) {
        Integer value = limits(plan).staff();
        return value == null ? Integer.MAX_VALUE : value;
    }

    public int maxIngredients(SubscriptionPlan plan) {
        Integer value = limits(plan).ingredients();
        return value == null ? Integer.MAX_VALUE : value;
    }

    private PlanDefinition toDefinition(SubscriptionPlanEntity entity) {
        try {
            JsonNode root = objectMapper.readTree(entity.getFeatureLimits());
            List<String> features = new ArrayList<>();
            JsonNode featureNode = root.path("features");
            if (featureNode.isArray()) {
                featureNode.forEach(item -> features.add(item.asText()));
            }
            return new PlanDefinition(
                    entity.getCode(),
                    entity.getPrice(),
                    new PlanLimits(
                            nullableInt(root.get("stores")),
                            nullableInt(root.get("staff")),
                            nullableInt(root.get("ingredients"))
                    ),
                    List.copyOf(features)
            );
        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid plan entitlement configuration"
            );
        }
    }

    private Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asInt();
    }
}
