package vn.inventoryai.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.alert.dto.AlertResponse;
import vn.inventoryai.common.security.SecurityUtils;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertRepository alertRepository;

    @GetMapping
    List<AlertResponse> listOpenAlerts() {
        Long storeId = SecurityUtils.storeId();
        return alertRepository.findAll().stream()
                .filter(alert -> alert.getStore().getId().equals(storeId) && !alert.isResolved())
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/resolve")
    AlertResponse resolve(@PathVariable Long id) {
        Alert alert = alertRepository.findById(id).orElseThrow();
        alert.setResolved(true);
        return toResponse(alertRepository.save(alert));
    }

    private AlertResponse toResponse(Alert alert) {
        String message = alert.getType() + " - " + alert.getIngredient().getName();
        return new AlertResponse(
                alert.getId(),
                alert.getStore().getId(),
                alert.getType(),
                alert.getIngredient().getId(),
                message,
                alert.isResolved() ? "RESOLVED" : "OPEN",
                alert.getCreatedAt()
        );
    }
}
