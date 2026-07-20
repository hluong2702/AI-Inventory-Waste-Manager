package vn.inventoryai.daily;

/**
 * Loại hành động trong Daily Action Center.
 * Phải khớp với CHECK constraint trong bảng daily_actions:
 * CHECK (action_type IN ('EXPIRY_RISK', 'REORDER', 'ANOMALY'))
 */
public enum DailyActionType {
    /** Lô hàng sắp hết hạn sử dụng */
    EXPIRY_RISK,
    /** Tồn kho thấp hơn mức an toàn, cần đặt thêm hàng */
    REORDER,
    /** Phát hiện bất thường trong lượng xuất kho (dự kiến triển khai sau) */
    ANOMALY
}
