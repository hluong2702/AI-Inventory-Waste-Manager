package vn.inventoryai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableMethodSecurity
@ConfigurationPropertiesScan
public class InventoryAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryAiApplication.class, args);
    }
}
