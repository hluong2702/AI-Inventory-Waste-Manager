package vn.inventoryai.daily;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.inventoryai.inventory.InventoryBatch;
import vn.inventoryai.inventory.Ingredient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hành động hàng ngày được tính toán tự động bởi hệ thống AI.
 * Map với bảng daily_actions (đã được tạo bởi V7 migration).
 *
 * Đây là bảng ghi nhận các tình huống cần người dùng can thiệp:
 * EXPIRY_RISK, REORDER, ANOMALY — sắp xếp theo priority_score DESC.
 */
@Getter
@Setter
@Entity
@Table(name = "daily_actions")
public class DailyAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID của store (tenant). Không dùng @ManyToOne để tránh lazy-load không cần thiết
     * trong batch computation jobs. Truy vấn luôn lọc theo cột này đầu tiên.
     */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private DailyActionType actionType;

    /**
     * Nguyên liệu liên quan. Bắt buộc có.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Ingredient product;

    /**
     * Lô hàng liên quan (chỉ áp dụng cho EXPIRY_RISK). Nullable.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    /**
     * Khoảng số lượng rủi ro (min-max). Null nếu không áp dụng.
     */
    @Column(name = "risk_qty_min", precision = 14, scale = 3)
    private BigDecimal riskQtyMin;

    @Column(name = "risk_qty_max", precision = 14, scale = 3)
    private BigDecimal riskQtyMax;

    /**
     * Ước tính giá trị thiệt hại/rủi ro tính theo VND.
     */
    @Column(name = "risk_value_estimate", nullable = false, precision = 16, scale = 2)
    private BigDecimal riskValueEstimate = BigDecimal.ZERO;

    /**
     * Điểm ưu tiên: số càng lớn thì hiển thị lên trên cùng.
     * Công thức tính phụ thuộc vào actionType.
     */
    @Column(name = "priority_score", nullable = false, precision = 20, scale = 6)
    private BigDecimal priorityScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyActionStatus status = DailyActionStatus.OPEN;

    /** Thời điểm hành động này được tính toán/cập nhật. */
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    /** Hành động hết hạn sau thời điểm này (null = không giới hạn). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Dữ liệu bổ sung dạng JSON (ví dụ: dayUntilExpiry, avgDailyUsage).
     * Lưu dưới dạng String để tránh phụ thuộc vào Jackson tại tầng Entity.
     */
    @Column(columnDefinition = "json", nullable = false)
    private String metadata = "{}";

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "dismiss_reason", length = 500)
    private String dismissReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (computedAt == null) computedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
