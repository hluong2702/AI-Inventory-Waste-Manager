package vn.inventoryai.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {
    @Test
    void declaresAuthenticationAndStoreContextContracts() {
        OpenAPI openAPI = new OpenApiConfig().inventoryAiOpenApi();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Inventory AI API");
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKeys("bearerAuth", "storeHeader");
        assertThat(openAPI.getComponents().getSecuritySchemes().get("storeHeader").getName())
                .isEqualTo("x-store-id");
    }
}
