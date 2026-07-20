package vn.inventoryai.daily;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.daily.dto.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * REST Controller cho Daily Action Center.
 *
 * Tất cả endpoint đều lọc theo tenantId từ TenantContext để đảm bảo
 * an toàn multi-tenant (người dùng của store A không thể xem store B).
 */
@RestController
@RequestMapping("/api/daily-actions")
@RequiredArgsConstructor
public class DailyActionController {

    private final DailyActionRepository dailyActionRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final DailyActionComputationService computationService;

    // ─── Danh sách hành động ──────────────────────────────────────────────────

    /**
     * Lấy danh sách hành động OPEN, sắp xếp theo priority_score giảm dần.
     * Mặc định trang đầu, tối đa 20 hành động.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'STAFF')")
    Page<DailyActionResponse> listOpenActions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long storeId = SecurityUtils.storeId();
        int boundedSize = Math.min(Math.max(size, 1), 100);
        return dailyActionRepository
                .findByTenantIdAndStatusOrderByPriorityScoreDesc(
                        storeId,
                        DailyActionStatus.OPEN,
                        PageRequest.of(page, boundedSize)
                )
                .map(this::toResponse);
    }

    /**
     * Đếm số hành động OPEN (dùng cho badge counter trên UI).
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'STAFF')")
    Map<String, Long> countOpenActions() {
        Long storeId = SecurityUtils.storeId();
        long count = dailyActionRepository.countByTenantIdAndStatus(storeId, DailyActionStatus.OPEN);
        return Map.of("openCount", count);
    }

    // ─── State transitions ────────────────────────────────────────────────────

    /**
     * Ghi nhận hành động (OPEN → ACKNOWLEDGED).
     * Người dùng đã xem và biết vấn đề, nhưng chưa giải quyết xong.
     */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Transactional
    DailyActionResponse acknowledge(@PathVariable Long id) {
        DailyAction action = requireOpenOrAcknowledged(id);

        if (action.getStatus() == DailyActionStatus.OPEN) {
            action.setStatus(DailyActionStatus.ACKNOWLEDGED);
            action.setAcknowledgedAt(Instant.now());
            dailyActionRepository.save(action);
        }
        return toResponse(action);
    }

    /**
     * Giải quyết hành động (OPEN/ACKNOWLEDGED → RESOLVED).
     * Người dùng xác nhận đã xử lý xong vấn đề.
     */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Transactional
    DailyActionResponse resolve(@PathVariable Long id) {
        DailyAction action = requireOpenOrAcknowledged(id);
        action.setStatus(DailyActionStatus.RESOLVED);
        action.setResolvedAt(Instant.now());
        dailyActionRepository.save(action);
        return toResponse(action);
    }

    /**
     * Bỏ qua hành động (OPEN/ACKNOWLEDGED → DISMISSED) với lý do.
     * Người dùng chủ động quyết định không xử lý vào lúc này.
     */
    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Transactional
    DailyActionResponse dismiss(
            @PathVariable Long id,
            @Valid @RequestBody DismissActionRequest request
    ) {
        DailyAction action = requireOpenOrAcknowledged(id);
        action.setStatus(DailyActionStatus.DISMISSED);
        action.setDismissedAt(Instant.now());
        action.setDismissReason(request.reason());
        dailyActionRepository.save(action);
        return toResponse(action);
    }

    // ─── Tenant Settings ──────────────────────────────────────────────────────

    /**
     * Lấy cấu hình ngưỡng tính toán của tenant.
     */
    @GetMapping("/settings")
    @PreAuthorize("hasRole('OWNER')")
    TenantSettingsResponse getSettings() {
        Long storeId = SecurityUtils.storeId();
        TenantSettings settings = tenantSettingsRepository.findByTenantId(storeId)
                .orElseGet(() -> defaultSettings(storeId));
        return toSettingsResponse(settings);
    }

    /**
     * Cập nhật cấu hình ngưỡng tính toán của tenant.
     */
    @PutMapping("/settings")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    TenantSettingsResponse updateSettings(@Valid @RequestBody UpdateTenantSettingsRequest request) {
        Long storeId = SecurityUtils.storeId();
        TenantSettings settings = tenantSettingsRepository.findByTenantId(storeId)
                .orElseGet(() -> {
                    TenantSettings s = new TenantSettings();
                    s.setTenantId(storeId);
                    return s;
                });

        settings.setExpiryWarningDays(request.expiryWarningDays());
        settings.setExpiryConsumptionLookbackDays(request.expiryConsumptionLookbackDays());
        settings.setReorderConsumptionLookbackDays(request.reorderConsumptionLookbackDays());
        settings.setReorderSafetyBufferDays(request.reorderSafetyBufferDays());
        settings.setReorderReviewPeriodDays(request.reorderReviewPeriodDays());
        settings.setAnomalyThresholdPercent(request.anomalyThresholdPercent());
        settings.setAnomalyMinAbsoluteQuantity(request.anomalyMinAbsoluteQuantity());
        settings.setDailyActionDisplayLimit(request.dailyActionDisplayLimit());

        return toSettingsResponse(tenantSettingsRepository.save(settings));
    }

    /**
     * Kích hoạt thủ công tái tính toán Daily Actions cho store hiện tại.
     * Dùng khi chủ cửa hàng muốn refresh ngay thay vì chờ cron.
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('OWNER')")
    Map<String, Object> refreshNow() {
        Long storeId = SecurityUtils.storeId();
        DailyActionComputationService.ComputationResult result =
                computationService.computeForStore(storeId);
        return Map.of(
                "expiryRiskCreated", result.expiryRiskCreated(),
                "expiryRiskUpdated", result.expiryRiskUpdated(),
                "reorderCreated", result.reorderCreated(),
                "reorderUpdated", result.reorderUpdated(),
                "anomalyCreated", result.anomalyCreated(),
                "anomalyUpdated", result.anomalyUpdated(),
                "autoResolved", result.autoResolved(),
                "cleanedUp", result.cleanedUp()
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private DailyAction requireOpenOrAcknowledged(Long id) {
        Long storeId = SecurityUtils.storeId();
        DailyAction action = dailyActionRepository.findByIdAndTenantId(id, storeId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Daily action not found in current store"
                ));
        if (action.getStatus() == DailyActionStatus.RESOLVED
                || action.getStatus() == DailyActionStatus.DISMISSED) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Hành động này đã ở trạng thái cuối (" + action.getStatus() + "), không thể thay đổi"
            );
        }
        return action;
    }

    private DailyActionResponse toResponse(DailyAction a) {
        return new DailyActionResponse(
                a.getId(),
                a.getTenantId(),
                a.getActionType(),
                a.getProduct().getId(),
                a.getProduct().getName(),
                a.getProduct().getCode(),
                a.getProduct().getUnit(),
                a.getBatch() != null ? a.getBatch().getId() : null,
                a.getBatch() != null ? a.getBatch().getBatchNumber() : null,
                a.getBatch() != null ? a.getBatch().getExpiryDate() : null,
                a.getTitle(),
                a.getDescription(),
                a.getRiskQtyMin(),
                a.getRiskQtyMax(),
                a.getRiskValueEstimate(),
                a.getPriorityScore(),
                a.getStatus(),
                a.getComputedAt(),
                a.getExpiresAt(),
                a.getAcknowledgedAt(),
                a.getResolvedAt(),
                a.getDismissedAt(),
                a.getDismissReason(),
                a.getCreatedAt()
        );
    }

    private TenantSettingsResponse toSettingsResponse(TenantSettings s) {
        return new TenantSettingsResponse(
                s.getTenantId(),
                s.getExpiryWarningDays(),
                s.getExpiryConsumptionLookbackDays(),
                s.getReorderConsumptionLookbackDays(),
                s.getReorderSafetyBufferDays(),
                s.getReorderReviewPeriodDays(),
                s.getAnomalyThresholdPercent(),
                s.getAnomalyMinAbsoluteQuantity(),
                s.getDailyActionDisplayLimit(),
                s.getDailyActionRefreshCron()
        );
    }

    private TenantSettings defaultSettings(Long storeId) {
        TenantSettings s = new TenantSettings();
        s.setTenantId(storeId);
        return s;
    }
}
