package vn.inventoryai.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.alert.dto.AlertResponse;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
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
        return alertRepository.findByStoreIdAndResolvedFalseOrderByCreatedAtDesc(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @Transactional
    AlertResponse resolve(@PathVariable Long id) {
        Alert alert = alertRepository.findByIdAndStoreId(id, SecurityUtils.storeId())
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Alert not found in current store"
                ));
        alert.setResolved(true);
        return toResponse(alert);
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
