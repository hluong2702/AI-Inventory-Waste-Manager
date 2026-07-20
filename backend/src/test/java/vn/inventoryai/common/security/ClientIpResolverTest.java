package vn.inventoryai.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {
    @Test
    void ignoresForwardingHeadersFromAnUntrustedImmediatePeer() {
        ClientIpResolver resolver = resolver(List.of("10.20.0.0/16"));
        MockHttpServletRequest request = request("198.51.100.10");
        request.addHeader("Forwarded", "for=203.0.113.20");
        request.addHeader("X-Forwarded-For", "203.0.113.21");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void resolvesTheFirstUntrustedAddressFromTheRightAcrossTrustedProxyHops() {
        ClientIpResolver resolver = resolver(List.of("10.20.0.0/16"));
        MockHttpServletRequest request = request("10.20.0.3");
        request.addHeader("X-Forwarded-For", "192.0.2.123, 203.0.113.42, 10.20.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void supportsRfcForwardedWithQuotedIpv6AndPort() {
        ClientIpResolver resolver = resolver(List.of("10.20.0.0/16", "2001:db8:1::/48"));
        MockHttpServletRequest request = request("10.20.0.3");
        request.addHeader("Forwarded", "for=198.51.100.90;proto=https, for=\"[2001:db8:1::5]:443\";by=10.20.0.3");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.90");
    }

    @Test
    void givesForwardedPrecedenceAndFailsClosedWhenItIsMalformed() {
        ClientIpResolver resolver = resolver(List.of("10.20.0.0/16"));
        MockHttpServletRequest request = request("10.20.0.3");
        request.addHeader("Forwarded", "for=_hidden");
        request.addHeader("X-Forwarded-For", "203.0.113.42");

        assertThat(resolver.resolve(request)).isEqualTo("10.20.0.3");
    }

    @Test
    void rejectsAnOversizedHeaderInsteadOfPartiallyParsingIt() {
        ClientIpResolver resolver = new ClientIpResolver(
                new ClientIpProperties(List.of("10.20.0.0/16"), 128, 8)
        );
        MockHttpServletRequest request = request("10.20.0.3");
        request.addHeader("X-Forwarded-For", "203.0.113.42," + "1".repeat(120));

        assertThat(resolver.resolve(request)).isEqualTo("10.20.0.3");
    }

    @Test
    void rejectsAChainThatExceedsTheConfiguredHopLimit() {
        ClientIpResolver resolver = new ClientIpResolver(
                new ClientIpProperties(List.of("10.20.0.0/16"), 4_096, 2)
        );
        MockHttpServletRequest request = request("10.20.0.3");
        request.addHeader("X-Forwarded-For", "203.0.113.42, 10.20.0.1, 10.20.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("10.20.0.3");
    }

    @Test
    void rejectsInvalidTrustedProxyConfigurationAtStartup() {
        assertThatThrownBy(() -> new ClientIpProperties(List.of("proxy.internal/24"), 4_096, 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("literal IP");
    }

    private ClientIpResolver resolver(List<String> trustedProxies) {
        return new ClientIpResolver(new ClientIpProperties(trustedProxies, 4_096, 16));
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
