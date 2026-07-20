package vn.inventoryai.subscription.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.payos")
public record PayOsProperties(
        boolean enabled,
        String clientId,
        String apiKey,
        String checksumKey,
        String returnUrl,
        String cancelUrl,
        int expireMinutes
) {
    public PayOsProperties {
        expireMinutes = expireMinutes <= 0 ? 15 : expireMinutes;
    }
}
