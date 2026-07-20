package vn.inventoryai.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.StockTransactionType;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.dto.CreateInventoryTransactionRequest;
import vn.inventoryai.inventory.dto.InventoryInRequest;
import vn.inventoryai.inventory.dto.InventoryOutRequest;
import vn.inventoryai.inventory.dto.InventoryTransactionResponse;
import vn.inventoryai.report.dto.StockTransactionResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final WasteRecordRepository wasteRecordRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public InventoryTransactionResponse in(InventoryInRequest request) {
        Ingredient ingredient = currentStoreIngredient(request.ingredientId());
        AppUser actor = currentUser();

        InventoryBatch batch = new InventoryBatch();
        batch.setStore(ingredient.getStore());
        batch.setIngredient(ingredient);
        batch.setBatchNumber("BATCH-" + clock.instant().toEpochMilli());
        batch.setQuantity(request.quantity());
        batch.setExpiryDate(request.expiryDate());
        batch.setCostPerUnit(ingredient.getUnitCost());
        batch = batchRepository.save(batch);

        StockTransaction tx = createTransaction(ingredient, batch, StockTransactionType.IN, request.quantity(), "IMPORT_NEW", ingredient.getUnitCost(), null, actor);
        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public List<InventoryTransactionResponse> out(InventoryOutRequest request) {
        Ingredient ingredient = currentStoreIngredient(request.ingredientId());
        return exportIngredient(ingredient, request.quantity(), "EXPORT_CONSUME", null).stream()
                .map(allocation -> toResponse(allocation.transaction()))
                .toList();
    }

    @Transactional
    public StockTransactionResponse createTransaction(CreateInventoryTransactionRequest request) {
        enforceTransactionPermission(request);
        return switch (request.type()) {
            case IMPORT -> importTransaction(request);
            case EXPORT -> exportTransaction(request);
        };
    }

    private void enforceTransactionPermission(CreateInventoryTransactionRequest request) {
        if (SecurityUtils.principal().role() != Role.STAFF
                || request.type() != CreateInventoryTransactionRequest.TransactionType.EXPORT) {
            return;
        }
        if (request.reason() == CreateInventoryTransactionRequest.TransactionReason.EXPORT_ADJUST
                || request.reason() == CreateInventoryTransactionRequest.TransactionReason.EXPORT_WASTE) {
            throw new AppException(
                    ErrorCode.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    "Staff cannot adjust inventory or record waste"
            );
        }
    }

    private StockTransactionResponse importTransaction(CreateInventoryTransactionRequest request) {
        List<StockTransactionResponse.Item> items = new ArrayList<>();
        StockTransaction last = null;
        AppUser actor = currentUser();
        for (CreateInventoryTransactionRequest.Item item : request.items()) {
            Ingredient ingredient = currentStoreIngredient(item.ingredientId());
            InventoryBatch batch = new InventoryBatch();
            batch.setStore(ingredient.getStore());
            batch.setIngredient(ingredient);
            batch.setBatchNumber(item.batchNumber() == null || item.batchNumber().isBlank()
                    ? "LOT-" + ingredient.getCode() + "-" + clock.instant().toEpochMilli()
                    : item.batchNumber());
            batch.setQuantity(item.quantity());
            batch.setExpiryDate(item.expiredDate());
            BigDecimal unitCost = item.costPerUnit() == null ? ingredient.getUnitCost() : item.costPerUnit();
            batch.setCostPerUnit(unitCost);
            batch = batchRepository.save(batch);
            ingredient.setUnitCost(unitCost);
            last = transactionRepository.save(createTransaction(ingredient, batch, StockTransactionType.IN, item.quantity(), request.reason().name(), unitCost, null, actor));
            items.add(new StockTransactionResponse.Item(ingredient.getId(), batch.getBatchNumber(), batch.getId(), item.quantity(), batch.getExpiryDate(), unitCost));
        }
        return toStockResponse(last, "IMPORT", request.reason().name(), items);
    }

    private StockTransactionResponse exportTransaction(CreateInventoryTransactionRequest request) {
        List<StockTransactionResponse.Item> items = new ArrayList<>();
        StockTransaction last = null;
        AppUser actor = currentUser();
        List<CreateInventoryTransactionRequest.Item> orderedItems = request.items().stream()
                .sorted(Comparator.comparing(CreateInventoryTransactionRequest.Item::ingredientId))
                .toList();
        for (CreateInventoryTransactionRequest.Item item : orderedItems) {
            Ingredient ingredient = currentStoreIngredient(item.ingredientId());
            List<ExportAllocation> exported = exportIngredient(ingredient, item.quantity(), request.reason().name(), request.wasteReason(), actor);
            items.addAll(exported.stream().map(ExportAllocation::item).toList());
            if (!exported.isEmpty()) {
                last = exported.get(exported.size() - 1).transaction();
            }
        }
        return toStockResponse(last, "EXPORT", request.reason().name(), items);
    }

    private List<ExportAllocation> exportIngredient(Ingredient ingredient, BigDecimal quantity, String reason, String wasteReason) {
        return exportIngredient(ingredient, quantity, reason, wasteReason, currentUser());
    }

    private List<ExportAllocation> exportIngredient(
            Ingredient ingredient,
            BigDecimal quantity,
            String reason,
            String wasteReason,
            AppUser actor
    ) {
        Long storeId = SecurityUtils.storeId();
        LocalDate businessDate = LocalDate.now(clock);
        List<InventoryBatch> batches = "EXPORT_CONSUME".equals(reason)
                ? batchRepository.lockSellableFefo(storeId, ingredient.getId(), businessDate)
                : batchRepository.lockPositiveFefo(storeId, ingredient.getId());
        BigDecimal available = batches.stream()
                .map(InventoryBatch::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT, "Không đủ tồn kho cho nguyên liệu " + ingredient.getName());
        }

        BigDecimal remaining = quantity;
        List<ExportAllocation> items = new ArrayList<>();

        for (InventoryBatch batch : batches) {
            if (remaining.signum() == 0) break;
            BigDecimal deducted = batch.getQuantity().min(remaining);
            batch.setQuantity(batch.getQuantity().subtract(deducted));
            remaining = remaining.subtract(deducted);
            StockTransaction tx = transactionRepository.save(createTransaction(ingredient, batch, StockTransactionType.OUT, deducted, reason, batch.getCostPerUnit(), wasteReason, actor));
            items.add(new ExportAllocation(tx, new StockTransactionResponse.Item(ingredient.getId(), batch.getBatchNumber(), batch.getId(), deducted, batch.getExpiryDate(), batch.getCostPerUnit())));
            if ("EXPORT_WASTE".equals(reason)) {
                createWasteRecord(ingredient, batch, deducted, wasteReason, batch.getCostPerUnit(), actor);
            }
        }
        if (remaining.signum() != 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT, "Không thể phân bổ đủ tồn kho cho nguyên liệu " + ingredient.getName());
        }
        return items;
    }

    private record ExportAllocation(StockTransaction transaction, StockTransactionResponse.Item item) {
    }

    private Ingredient currentStoreIngredient(Long ingredientId) {
        Long storeId = SecurityUtils.storeId();
        return ingredientRepository.findByIdAndStoreIdAndDeletedFalse(ingredientId, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found in current store"));
    }

    private void createWasteRecord(
            Ingredient ingredient,
            InventoryBatch batch,
            BigDecimal quantity,
            String wasteReason,
            BigDecimal unitCost,
            AppUser actor
    ) {
        WasteRecord record = new WasteRecord();
        record.setStore(ingredient.getStore());
        record.setIngredient(ingredient);
        record.setBatch(batch);
        record.setQuantity(quantity);
        record.setReason(wasteReason == null || wasteReason.isBlank() ? "OTHER" : wasteReason);
        record.setEstimatedCost(unitCost.multiply(quantity));
        record.setCreatedBy(actor);
        wasteRecordRepository.save(record);
    }

    private AppUser currentUser() {
        return userRepository.findById(SecurityUtils.principal().userId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
    }

    private StockTransaction createTransaction(
            Ingredient ingredient,
            InventoryBatch batch,
            StockTransactionType type,
            BigDecimal quantity,
            String reason,
            BigDecimal unitCost,
            String wasteReason,
            AppUser actor
    ) {
        StockTransaction tx = new StockTransaction();
        tx.setStore(ingredient.getStore());
        tx.setIngredient(ingredient);
        tx.setBatch(batch);
        tx.setType(type);
        tx.setReason(reason == null || reason.isBlank() ? (type == StockTransactionType.IN ? "IMPORT_NEW" : "EXPORT_CONSUME") : reason);
        tx.setQuantity(quantity);
        tx.setUnitCost(unitCost == null ? BigDecimal.ZERO : unitCost);
        tx.setWasteReason(wasteReason);
        tx.setCreatedBy(actor);
        return tx;
    }

    private StockTransactionResponse toStockResponse(StockTransaction tx, String type, String reason, List<StockTransactionResponse.Item> items) {
        if (tx == null) {
            return new StockTransactionResponse(null, SecurityUtils.storeId(), type, reason, clock.instant(), SecurityUtils.principal().email(), items);
        }
        return new StockTransactionResponse(tx.getId(), tx.getStore().getId(), type, reason, tx.getCreatedAt(), tx.getCreatedBy().getEmail(), items);
    }

    private InventoryTransactionResponse toResponse(StockTransaction tx) {
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getIngredient().getId(),
                tx.getBatch() == null ? null : tx.getBatch().getId(),
                tx.getType(),
                tx.getQuantity()
        );
    }
}
