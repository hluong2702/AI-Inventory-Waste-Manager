package vn.inventoryai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.business")
public record BusinessTimeProperties(String timeZone) {
    public BusinessTimeProperties {
        timeZone = timeZone == null ? "" : timeZone.trim();
        if (timeZone.isEmpty()) {
            throw new IllegalArgumentException("app.business.time-zone is required");
        }
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("app.business.time-zone is invalid", ex);
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }
}
