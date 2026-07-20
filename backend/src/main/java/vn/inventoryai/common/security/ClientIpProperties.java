package vn.inventoryai.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app.network.client-ip")
public record ClientIpProperties(
        List<String> trustedProxies,
        int maxHeaderLength,
        int maxHops
) {
    public ClientIpProperties {
        trustedProxies = trustedProxies == null
                ? List.of()
                : trustedProxies.stream()
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (trustedProxies.size() > 64) {
            throw new IllegalArgumentException("app.network.client-ip.trusted-proxies supports at most 64 CIDRs");
        }
        trustedProxies.forEach(IpNetwork::parse);
        if (maxHeaderLength < 128 || maxHeaderLength > 16_384) {
            throw new IllegalArgumentException("app.network.client-ip.max-header-length must be between 128 and 16384");
        }
        if (maxHops < 1 || maxHops > 64) {
            throw new IllegalArgumentException("app.network.client-ip.max-hops must be between 1 and 64");
        }
    }
}
