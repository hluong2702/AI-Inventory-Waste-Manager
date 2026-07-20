package vn.inventoryai.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.admin.dto.*;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.auth.TenantMembership;
import vn.inventoryai.auth.TenantMembershipRepository;
import vn.inventoryai.common.enums.StoreStatus;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.enums.UserStatus;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.inventory.StockTransactionRepository;
import vn.inventoryai.inventory.IngredientRepository;
import vn.inventoryai.inventory.WasteRecordRepository;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.SubscriptionStatus;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final StockTransactionRepository transactionRepository;
    private final IngredientRepository ingredientRepository;
    private final WasteRecordRepository wasteRecordRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        LocalDate today = LocalDate.now(clock);
        BigDecimal mrr = subscriptionRepository.sumActiveMonthlyRecurringRevenue();
        if (mrr == null) mrr = BigDecimal.ZERO;

        List<StoreActivityResponse> mostActive = transactionRepository
                .findMostActiveStores(PageRequest.of(0, 10))
                .stream()
                .map(activity -> new StoreActivityResponse(
                        activity.getStoreId(),
                        activity.getStoreName(),
                        activity.getTransactionCount() == null ? 0 : activity.getTransactionCount()
                ))
                .toList();

        return new AdminDashboardResponse(
                storeRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                mrr,
                subscriptionRepository.countByStatusAndEndDateBetween(
                        SubscriptionStatus.ACTIVE,
                        today,
                        today.plusDays(7)
                ),
                storeRepository.countByCreatedAtAfter(today.atStartOfDay(clock.getZone()).toInstant()),
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
                zero(wasteRecordRepository.sumEstimatedCostAllStores())
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> users(Pageable pageable) {
        var users = userRepository.findAllWithStore(boundedPageable(
                        pageable,
                        Set.of("id", "email", "fullName", "role", "status", "createdAt"),
                        Sort.by(Sort.Order.desc("id"))
                ));
        Map<Long, List<TenantMembership>> membershipsByUser = users.isEmpty()
                ? Map.of()
                : membershipRepository.findAllByUserIdInOrderByStoreNameAsc(
                                users.getContent().stream().map(user -> user.getId()).toList()
                        )
                        .stream()
                        .collect(Collectors.groupingBy(membership -> membership.getUser().getId()));
        return users.map(user -> new AdminUserResponse(
                        user.getId(),
                        user.getStore() == null ? null : user.getStore().getId(),
                        user.getEmail(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        user.getStatus(),
                        membershipsByUser.getOrDefault(user.getId(), List.of()).stream()
                                .map(membership -> new AdminUserResponse.Membership(
                                        membership.getStore().getId(),
                                        membership.getStore().getName(),
                                        membership.getRole(),
                                        membership.getStatus()
                                ))
                                .toList()
                ));
    }

    @Transactional(readOnly = true)
    public Page<AdminStoreResponse> stores(SubscriptionPlan plan, StoreStatus status, Pageable pageable) {
        Pageable boundedPageable = boundedPageable(
                pageable,
                Set.of("id", "name", "subscriptionPlan", "status", "createdAt"),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<Store> stores;
        if (plan != null && status != null) {
            stores = storeRepository.findBySubscriptionPlanAndStatus(plan, status, boundedPageable);
        } else if (plan != null) {
            stores = storeRepository.findBySubscriptionPlan(plan, boundedPageable);
        } else if (status != null) {
            stores = storeRepository.findByStatus(status, boundedPageable);
        } else {
            stores = storeRepository.findAll(boundedPageable);
        }
        return stores.map(this::toResponse);
    }

    @Transactional
    public AdminStoreResponse updateStatus(Long storeId, UpdateStoreStatusRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        store.setStatus(request.status());
        return toResponse(storeRepository.save(store));
    }

    private AdminStoreResponse toResponse(Store store) {
        return new AdminStoreResponse(
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                store.getSubscriptionPlan(),
                store.getStatus(),
                store.getCreatedAt()
        );
    }

    private Pageable boundedPageable(
            Pageable requested,
            Set<String> allowedSorts,
            Sort defaultSort
    ) {
        int page = requested == null || requested.isUnpaged() ? 0 : Math.max(requested.getPageNumber(), 0);
        int size = requested == null || requested.isUnpaged()
                ? 20
                : Math.min(Math.max(requested.getPageSize(), 1), 100);
        List<Sort.Order> orders = new ArrayList<>();
        if (requested != null) {
            requested.getSort().forEach(order -> {
                if (allowedSorts.contains(order.getProperty())) orders.add(order);
            });
        }
        if (orders.isEmpty()) defaultSort.forEach(orders::add);
        if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
            orders.add(Sort.Order.desc("id"));
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
