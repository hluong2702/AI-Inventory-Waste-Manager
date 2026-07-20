package vn.inventoryai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    @Bean
    Clock clock(BusinessTimeProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
