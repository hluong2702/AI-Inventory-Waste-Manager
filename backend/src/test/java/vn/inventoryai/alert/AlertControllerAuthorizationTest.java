package vn.inventoryai.alert;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AlertControllerAuthorizationTest {

    @Test
    void resolvingAnAlertRequiresManagerOrOwner() throws NoSuchMethodException {
        Method resolve = AlertController.class.getDeclaredMethod("resolve", Long.class);

        PreAuthorize authorization = resolve.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyRole('OWNER','MANAGER')");
    }
}
