package vn.inventoryai.daily;

/**
 * Trạng thái vòng đời của một DailyAction.
 * Phải khớp với CHECK constraint trong bảng daily_actions:
 * CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED'))
 */
public enum DailyActionStatus {
    /** Chưa được xử lý */
    OPEN,
    /** Đã được ghi nhận (người dùng đã xem và biết) */
    ACKNOWLEDGED,
    /** Đã giải quyết xong */
    RESOLVED,
    /** Bỏ qua có lý do */
    DISMISSED
}
