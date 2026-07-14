package vn.inventoryai.inventory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.inventoryai.inventory.dto.InventoryInRequest;
import vn.inventoryai.inventory.dto.InventoryBatchResponse;
import vn.inventoryai.inventory.dto.InventoryOutRequest;
import vn.inventoryai.inventory.dto.InventoryTransactionResponse;
import vn.inventoryai.inventory.dto.CreateInventoryTransactionRequest;
import vn.inventoryai.report.dto.StockTransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;
    private final InventoryBatchRepository batchRepository;

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
    Page<InventoryBatchResponse> batches(Pageable pageable) {
        Long storeId = vn.inventoryai.common.security.SecurityUtils.storeId();
        return batchRepository.findByStoreId(storeId, pageable)
                .map(batch -> new InventoryBatchResponse(
                        batch.getId(),
                        storeId,
                        batch.getIngredient().getId(),
                        batch.getBatchNumber(),
                        batch.getQuantity(),
                        batch.getExpiryDate(),
                        batch.getReceivedAt(),
                        batch.getCostPerUnit()
                ));
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    StockTransactionResponse createTransaction(@Valid @RequestBody CreateInventoryTransactionRequest request) {
        return inventoryService.createTransaction(request);
    }

    @PostMapping("/transactions/legacy")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    List<InventoryTransactionResponse> legacyCreateTransaction(@RequestBody Map<String, Object> payload) {
        String type = String.valueOf(payload.get("type"));
        Object rawItems = payload.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .flatMap(item -> {
                    Map<?, ?> map = (Map<?, ?>) item;
                    Long ingredientId = ((Number) map.get("ingredientId")).longValue();
                    BigDecimal quantity = new BigDecimal(String.valueOf(map.get("quantity")));
                    if ("IMPORT".equals(type)) {
                        LocalDate expiredDate = LocalDate.parse(String.valueOf(map.get("expiredDate")));
                        return java.util.stream.Stream.of(in(new InventoryInRequest(ingredientId, quantity, expiredDate)));
                    }
                    return out(new InventoryOutRequest(ingredientId, quantity)).stream();
                })
                .toList();
    }
}
