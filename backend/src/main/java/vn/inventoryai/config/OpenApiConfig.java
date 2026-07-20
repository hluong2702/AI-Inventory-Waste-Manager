package vn.inventoryai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String STORE_HEADER = "storeHeader";

    @Bean
    OpenAPI inventoryAiOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(STORE_HEADER, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("x-store-id"));

        return new OpenAPI()
                .info(new Info()
                        .title("Inventory AI API")
                        .version("1.0")
                        .description("API contract for inventory, waste, staff, billing and reporting."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH).addList(STORE_HEADER));
    }
}
