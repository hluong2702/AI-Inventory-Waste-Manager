package vn.inventoryai.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessTimePropertiesTest {
    @Test
    void resolvesConfiguredBusinessZone() {
        BusinessTimeProperties properties = new BusinessTimeProperties("Asia/Ho_Chi_Minh");

        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void rejectsMissingOrInvalidZone() {
        assertThatThrownBy(() -> new BusinessTimeProperties(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessTimeProperties("Mars/Olympus"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
