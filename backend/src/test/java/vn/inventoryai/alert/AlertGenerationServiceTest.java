package vn.inventoryai.alert;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertGenerationServiceTest {

    @Test
    void resolvesRecoveredConditionsThenCreatesOnlyCurrentlyMissingAlerts() {
        AlertRepository repository = mock(AlertRepository.class);
        AlertGenerationService service = new AlertGenerationService(repository);
        LocalDate businessDate = LocalDate.parse("2026-07-14");
        LocalDate expiryThreshold = LocalDate.parse("2026-07-17");

        when(repository.resolveRecoveredLowStock(17L, businessDate)).thenReturn(2);
        when(repository.resolveClearedExpiryRisk(17L, expiryThreshold)).thenReturn(1);
        when(repository.createMissingLowStock(17L, businessDate)).thenReturn(3);
        when(repository.createMissingExpiryRisk(17L, expiryThreshold)).thenReturn(4);

        var result = service.generateForStore(17L, businessDate, expiryThreshold);

        assertThat(result).isEqualTo(new AlertGenerationService.AlertGenerationResult(3, 4, 2, 1));
        var ordered = inOrder(repository);
        ordered.verify(repository).resolveRecoveredLowStock(17L, businessDate);
        ordered.verify(repository).resolveClearedExpiryRisk(17L, expiryThreshold);
        ordered.verify(repository).createMissingLowStock(17L, businessDate);
        ordered.verify(repository).createMissingExpiryRisk(17L, expiryThreshold);
    }

    @Test
    void eachStoreRunsInAnIndependentTransaction() throws NoSuchMethodException {
        Method method = AlertGenerationService.class.getMethod(
                "generateForStore",
                Long.class,
                LocalDate.class,
                LocalDate.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
