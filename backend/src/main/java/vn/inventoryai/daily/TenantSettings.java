package vn.inventoryai.daily;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cấu hình tham số tính toán Daily Action Center theo từng tenant.
 * Map với bảng tenant_settings (đã được tạo bởi V7 migration).
 *
 * Mỗi tenant có đúng một bản ghi settings (UNIQUE constraint trên tenant_id).
 * Hệ thống tự tạo bản ghi mặc định khi tenant đăng ký mới.
 */
@Getter
@Setter
@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    /**
     * Số ngày trước khi hết hạn để tạo cảnh báo EXPIRY_RISK (mặc định 3 ngày).
     */
    @Column(name = "expiry_warning_days", nullable = false)
    private int expiryWarningDays = 3;

    /**
     * Số ngày nhìn lại để tính mức tiêu thụ trung bình cho EXPIRY_RISK (mặc định 28 ngày).
     */
    @Column(name = "expiry_consumption_lookback_days", nullable = false)
    private int expiryConsumptionLookbackDays = 28;

    /**
     * Số ngày nhìn lại để tính mức tiêu thụ trung bình cho REORDER (mặc định 14 ngày).
     */
    @Column(name = "reorder_consumption_lookback_days", nullable = false)
    private int reorderConsumptionLookbackDays = 14;

    /**
     * Số ngày đệm an toàn khi tính lượng hàng cần đặt (mặc định 0.5 ngày).
     */
    @Column(name = "reorder_safety_buffer_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal reorderSafetyBufferDays = new BigDecimal("0.50");

    /**
     * Chu kỳ review (số ngày cần đủ hàng) khi tính lượng đặt REORDER (mặc định 7 ngày).
     */
    @Column(name = "reorder_review_period_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal reorderReviewPeriodDays = new BigDecimal("7.00");

    /**
     * Ngưỡng % lệch để phát hiện bất thường ANOMALY (mặc định 25%).
     */
    @Column(name = "anomaly_threshold_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal anomalyThresholdPercent = new BigDecimal("25.00");

    /**
     * Lượng tối thiểu tuyệt đối để xét bất thường ANOMALY (mặc định 1.000 đơn vị).
     */
    @Column(name = "anomaly_min_absolute_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal anomalyMinAbsoluteQuantity = new BigDecimal("1.000");

    /**
     * Số lượng hành động tối đa hiển thị trên Dashboard (mặc định 10).
     */
    @Column(name = "daily_action_display_limit", nullable = false)
    private int dailyActionDisplayLimit = 10;

    /**
     * Cron expression để refresh Daily Actions (mặc định mỗi giờ).
     */
    @Column(name = "daily_action_refresh_cron", nullable = false, length = 80)
    private String dailyActionRefreshCron = "0 0 * * * *";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
