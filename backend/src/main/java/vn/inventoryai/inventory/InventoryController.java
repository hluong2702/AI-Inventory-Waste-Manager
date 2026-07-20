package vn.inventoryai.inventory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.dto.InventoryInRequest;
import vn.inventoryai.inventory.dto.InventoryBatchResponse;
import vn.inventoryai.inventory.dto.InventoryOutRequest;
import vn.inventoryai.inventory.dto.InventorySummaryResponse;
import vn.inventoryai.inventory.dto.InventoryTransactionResponse;
import vn.inventoryai.inventory.dto.CreateInventoryTransactionRequest;
import vn.inventoryai.report.dto.StockTransactionResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;
    private final InventoryBatchRepository batchRepository;
    private final Clock clock;

    @PostMapping("/in")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    InventoryTransactionResponse in(@Valid @RequestBody InventoryInRequest request) {
        return inventoryService.in(request);
    }

    @PostMapping("/out")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    List<InventoryTransactionResponse> out(@Valid @RequestBody InventoryOutRequest request) {
        return inventoryService.out(request);
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    Page<InventoryBatchResponse> batches(
            @RequestParam(required = false) Long ingredientId,
            Pageable pageable
    ) {
        Long storeId = SecurityUtils.storeId();
        Pageable boundedPageable = boundedBatchPageable(pageable);
        Page<InventoryBatch> batchPage = ingredientId == null
                ? batchRepository.findByStoreIdAndQuantityGreaterThan(storeId, BigDecimal.ZERO, boundedPageable)
                : batchRepository.findByStoreIdAndIngredientIdAndQuantityGreaterThan(
                        storeId,
                        ingredientId,
                        BigDecimal.ZERO,
                        boundedPageable
                );
        return batchPage
                .map(batch -> new InventoryBatchResponse(
                        batch.getId(),
                        storeId,
                        batch.getIngredient().getId(),
                        batch.getIngredient().getName(),
                        batch.getIngredient().getUnit(),
                        batch.getIngredient().getCategory(),
                        batch.getBatchNumber(),
                        batch.getQuantity(),
                        batch.getExpiryDate(),
                        batch.getReceivedAt(),
                        batch.getCostPerUnit()
                ));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    Page<InventorySummaryResponse> summary(Pageable pageable) {
        Long storeId = SecurityUtils.storeId();
        LocalDate businessDate = LocalDate.now(clock);
        Pageable boundedPageable = boundedSummaryPageable(pageable);
        return batchRepository.summarizeInventory(storeId, businessDate, businessDate.plusDays(3), boundedPageable)
                .map(row -> new InventorySummaryResponse(
                        row.getIngredientId(),
                        row.getCode(),
                        row.getName(),
                        row.getUnit(),
                        row.getCategory(),
                        row.getMinStock(),
                        row.getMaxStock(),
                        row.getTotalQuantity(),
                        row.getSellableQuantity(),
                        row.getActiveBatchesCount() == null ? 0 : row.getActiveBatchesCount(),
                        row.getExpiredBatchesCount() == null ? 0 : row.getExpiredBatchesCount(),
                        row.getExpiringSoonBatchesCount() == null ? 0 : row.getExpiringSoonBatchesCount()
                ));
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    StockTransactionResponse createTransaction(@Valid @RequestBody CreateInventoryTransactionRequest request) {
        return inventoryService.createTransaction(request);
    }

    private Pageable boundedSummaryPageable(Pageable requested) {
        int page = requested.isPaged() ? Math.max(requested.getPageNumber(), 0) : 0;
        int size = requested.isPaged() ? Math.min(Math.max(requested.getPageSize(), 1), 100) : 20;
        return PageRequest.of(page, size);
    }

    private Pageable boundedBatchPageable(Pageable requested) {
        int page = requested.isPaged() ? Math.max(requested.getPageNumber(), 0) : 0;
        int size = requested.isPaged() ? Math.min(Math.max(requested.getPageSize(), 1), 100) : 20;
        Set<String> allowedProperties = Set.of("expiryDate", "receivedAt", "batchNumber", "quantity", "id");
        List<Sort.Order> orders = new ArrayList<>();
        requested.getSort().forEach(order -> {
            if (allowedProperties.contains(order.getProperty())) {
                orders.add(order);
            }
        });
        if (orders.isEmpty()) {
            orders.add(Sort.Order.asc("expiryDate"));
        }
        if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
            orders.add(Sort.Order.asc("id"));
        }
        return PageRequest.of(page, size, Sort.by(orders));
    }
}
