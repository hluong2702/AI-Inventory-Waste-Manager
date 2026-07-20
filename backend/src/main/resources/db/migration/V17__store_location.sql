-- V17: Thêm tọa độ địa lý vào bảng stores để gọi thời tiết chính xác theo vị trí.
-- Mặc định TP.HCM (10.823099, 106.629662) — hoạt động ngay mà không cần user nhập.
-- Sau này có thể thêm UI cho Owner tự nhập địa chỉ cửa hàng.

ALTER TABLE stores
    ADD COLUMN latitude  DECIMAL(9, 6) NOT NULL DEFAULT 10.823099
        COMMENT 'Vĩ độ cửa hàng (WGS84). Default: TP.HCM',
    ADD COLUMN longitude DECIMAL(9, 6) NOT NULL DEFAULT 106.629662
        COMMENT 'Kinh độ cửa hàng (WGS84). Default: TP.HCM';
