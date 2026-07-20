package vn.inventoryai.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendPropertiesTest {
    @Test
    void acceptsHttpsProductionUrls() {
        FrontendProperties properties = new FrontendProperties(
                URI.create("https://app.inventory.example/login"),
                URI.create("https://app.inventory.example/accept-invite")
        );

        assertThat(properties.inviteUrl().getScheme()).isEqualTo("https");
    }

    @Test
    void acceptsPlainHttpOnlyForLoopbackDevelopment() {
        FrontendProperties properties = new FrontendProperties(
                URI.create("http://localhost:5173/login"),
                URI.create("http://127.0.0.1:5173/accept-invite")
        );

        assertThat(properties.loginUrl().getHost()).isEqualTo("localhost");
    }

    @Test
    void rejectsInsecureNonLoopbackAndTokenBearingBaseUrls() {
        assertThatThrownBy(() -> new FrontendProperties(
                URI.create("http://app.inventory.example/login"),
                URI.create("https://app.inventory.example/accept-invite")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");

        assertThatThrownBy(() -> new FrontendProperties(
                URI.create("https://app.inventory.example/login"),
                URI.create("https://app.inventory.example/accept-invite#token=unsafe")
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fragments");
    }
}
