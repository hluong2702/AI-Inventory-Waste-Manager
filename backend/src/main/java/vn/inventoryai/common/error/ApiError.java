package vn.inventoryai.common.error;

import java.time.Instant;

public record ApiError(
        ErrorCode code,
        String message,
        Instant timestamp
) {
    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code, message, Instant.now());
    }
}
