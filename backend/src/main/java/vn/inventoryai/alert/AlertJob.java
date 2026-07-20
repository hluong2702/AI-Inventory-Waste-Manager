package vn.inventoryai.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.observability.OperationalMetrics;
import vn.inventoryai.store.StoreRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertJob {
    private final StoreRepository storeRepository;
    private final AlertGenerationService alertGenerationService;
    private final Clock clock;
    private final OperationalMetrics operationalMetrics;

    @Value("${app.alerts.expiring-days:3}")
    private long expiringDays;

    @Value("${app.alerts.store-page-size:100}")
    private int configuredStorePageSize;

    @Value("${app.alerts.zone:Asia/Ho_Chi_Minh}")
    private String alertZone;

    @Scheduled(
            cron = "${app.alerts.cron:0 15 2 * * *}",
            zone = "${app.alerts.zone:Asia/Ho_Chi_Minh}"
    )
    public void generateDailyAlerts() {
        LocalDate businessDate = LocalDate.now(clock.withZone(ZoneId.of(alertZone)));
        long safeExpiringDays = Math.max(0, Math.min(expiringDays, 365));
        LocalDate expiryThreshold = businessDate.plusDays(safeExpiringDays);
        int pageSize = Math.max(1, Math.min(configuredStorePageSize, 500));
        long afterStoreId = 0L;
        int processedStores = 0;
        int failedStores = 0;

        while (true) {
            List<Long> storeIds = storeRepository.findIdsByStatusAfter(
                    StoreStatus.ACTIVE,
                    afterStoreId,
                    PageRequest.of(0, pageSize)
            );
            if (storeIds.isEmpty()) {
                break;
            }

            for (Long storeId : storeIds) {
                try {
                    alertGenerationService.generateForStore(storeId, businessDate, expiryThreshold);
                    operationalMetrics.alertGeneration("success");
                    processedStores++;
                } catch (RuntimeException failure) {
                    operationalMetrics.alertGeneration("failure");
                    failedStores++;
                    log.error("Alert generation failed for storeId={}", storeId, failure);
                }
                afterStoreId = storeId;
            }

            if (storeIds.size() < pageSize) {
                break;
            }
        }

        log.info(
                "Daily alert generation completed: processedStores={}, failedStores={}, businessDate={}",
                processedStores,
                failedStores,
                businessDate
        );
    }
}
