package vn.inventoryai.daily.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body khi người dùng Dismiss một hành động.
 * Phải cung cấp lý do để hệ thống lưu truy vết.
 */
public record DismissActionRequest(
        @NotBlank(message = "Cần cung cấp lý do bỏ qua")
        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
        String reason
) {
}
