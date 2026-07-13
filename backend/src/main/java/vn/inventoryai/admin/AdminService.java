package vn.inventoryai.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.admin.dto.*;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.store.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.*;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StockTransactionRepository transactionRepository;
    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        LocalDate today = LocalDate.now();
        BigDecimal mrr = subscriptionRepository.findAll().stream()
                .filter(subscription -> subscription.isActive() && subscription.getPlan() != SubscriptionPlan.FREE)
                .map(subscription -> subscription.getPlan() == SubscriptionPlan.BASIC ? BigDecimal.valueOf(299_000) : BigDecimal.valueOf(699_000))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StoreActivityResponse> mostActive = storeRepository.findAll().stream()
                .map(store -> new StoreActivityResponse(store.getId(), store.getName(), transactionRepository.countByStoreId(store.getId())))
                .sorted(Comparator.comparingLong(StoreActivityResponse::transactionCount).reversed())
                .limit(10)
                .toList();

        return new AdminDashboardResponse(
                storeRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                mrr,
                subscriptionRepository.countByActiveTrueAndExpiresAtBetween(today, today.plusDays(7)),
                storeRepository.countByCreatedAtAfter(today.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                mostActive
        );
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        return new AdminStatsResponse(
                storeRepository.count(),
                userRepository.count(),
                ingredientRepository.count(),
                transactionRepository.count(),
                BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users() {
        return userRepository.findAll().stream()
                .map(user -> new AdminUserResponse(
                        user.getId(),
                        user.getStore() == null ? null : user.getStore().getId(),
                        user.getEmail(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        user.getStatus()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminStoreResponse> stores(SubscriptionPlan plan, StoreStatus status) {
        List<Store> stores;
        if (plan != null && status != null) {
            stores = storeRepository.findBySubscriptionPlanAndStatus(plan, status);
        } else if (plan != null) {
            stores = storeRepository.findBySubscriptionPlan(plan);
        } else if (status != null) {
            stores = storeRepository.findByStatus(status);
        } else {
            stores = storeRepository.findAll();
        }
        return stores.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminStoreResponse updateStatus(Long storeId, UpdateStoreStatusRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        store.setStatus(request.status());
        return toResponse(storeRepository.save(store));
    }

    private AdminStoreResponse toResponse(Store store) {
        return new AdminStoreResponse(store.getId(), store.getName(), store.getSubscriptionPlan(), store.getStatus(), store.getCreatedAt());
    }
}
