package vn.inventoryai.daily;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.observability.OperationalMetrics;
import vn.inventoryai.store.StoreRepository;

import java.util.List;

/**
 * Scheduled job tính toán Daily Actions định kỳ cho tất cả các store đang ACTIVE.
 *
 * Chạy mặc định mỗi giờ (0 0 * * * *). Mỗi store được xử lý trong transaction
 * REQUIRES_NEW riêng biệt, đảm bảo lỗi của một store không ảnh hưởng các store khác.
 *
 * Thiết kế giống AlertJob — phân trang qua stores để xử lý bộ nhớ hiệu quả
 * khi số lượng store lớn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyActionJob {

    private final StoreRepository storeRepository;
    private final DailyActionComputationService computationService;
    private final OperationalMetrics operationalMetrics;

    @Value("${app.daily-actions.store-page-size:100}")
    private int configuredStorePageSize;

    @Scheduled(
            cron = "${app.daily-actions.cron:0 0 * * * *}",
            zone = "${app.daily-actions.zone:${app.business.time-zone:Asia/Ho_Chi_Minh}}"
    )
    public void computeAllStores() {
        long afterStoreId = 0L;
        int pageSize = Math.max(1, Math.min(configuredStorePageSize, 500));
        int processedStores = 0;
        int failedStores = 0;
        int totalExpiryRiskCreated = 0;
        int totalExpiryRiskUpdated = 0;
        int totalReorderCreated = 0;
        int totalReorderUpdated = 0;

        log.info("Daily action computation started");

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
                    DailyActionComputationService.ComputationResult result =
                            computationService.computeForStore(storeId);

                    totalExpiryRiskCreated += result.expiryRiskCreated();
                    totalExpiryRiskUpdated += result.expiryRiskUpdated();
                    totalReorderCreated += result.reorderCreated();
                    totalReorderUpdated += result.reorderUpdated();

                    operationalMetrics.alertGeneration("daily_action_success");
                    processedStores++;
                } catch (RuntimeException ex) {
                    operationalMetrics.alertGeneration("daily_action_failure");
                    failedStores++;
                    log.error("Daily action computation failed for storeId={}", storeId, ex);
                }
                afterStoreId = storeId;
            }

            if (storeIds.size() < pageSize) {
                break;
            }
        }

        log.info(
                "Daily action computation completed: processedStores={}, failedStores={}, " +
                "expiryRisk(created={}, updated={}), reorder(created={}, updated={})",
                processedStores, failedStores,
                totalExpiryRiskCreated, totalExpiryRiskUpdated,
                totalReorderCreated, totalReorderUpdated
        );
    }
}
