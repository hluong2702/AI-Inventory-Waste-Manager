package vn.inventoryai.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AlertGenerationService {
    private final AlertRepository alertRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlertGenerationResult generateForStore(
            Long storeId,
            LocalDate businessDate,
            LocalDate expiryThreshold
    ) {
        int resolvedLowStock = alertRepository.resolveRecoveredLowStock(storeId, businessDate);
        int resolvedExpiryRisk = alertRepository.resolveClearedExpiryRisk(storeId, expiryThreshold);
        int createdLowStock = alertRepository.createMissingLowStock(storeId, businessDate);
        int createdExpiryRisk = alertRepository.createMissingExpiryRisk(storeId, expiryThreshold);

        return new AlertGenerationResult(
                createdLowStock,
                createdExpiryRisk,
                resolvedLowStock,
                resolvedExpiryRisk
        );
    }

    public record AlertGenerationResult(
            int createdLowStock,
            int createdExpiryRisk,
            int resolvedLowStock,
            int resolvedExpiryRisk
    ) {
    }
}
