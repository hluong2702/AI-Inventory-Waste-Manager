package vn.inventoryai.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.inventoryai.auth.AppUser;
import vn.inventoryai.auth.UserRepository;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.inventory.dto.CreateInventoryTransactionRequest;
import vn.inventoryai.inventory.dto.InventoryOutRequest;
import vn.inventoryai.store.Store;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {
    private static final Long STORE_ID = 99L;
    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.parse("2026-07-14");

    private IngredientRepository ingredientRepository;
    private InventoryBatchRepository batchRepository;
    private StockTransactionRepository transactionRepository;
    private WasteRecordRepository wasteRecordRepository;
    private InventoryService service;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        batchRepository = mock(InventoryBatchRepository.class);
        transactionRepository = mock(StockTransactionRepository.class);
        wasteRecordRepository = mock(WasteRecordRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T08:00:00Z"), ZoneId.of("UTC"));

        Store store = new Store();
        store.setId(STORE_ID);
        AppUser actor = new AppUser();
        actor.setId(USER_ID);
        actor.setEmail("staff@example.com");
        actor.setStore(store);

        ingredient = new Ingredient();
        ingredient.setId(1L);
        ingredient.setStore(store);
        ingredient.setCode("MILK");
        ingredient.setName("Sữa");
        ingredient.setUnit("lít");
        ingredient.setUnitCost(new BigDecimal("20000"));

        when(ingredientRepository.findByIdAndStoreIdAndDeletedFalse(ingredient.getId(), STORE_ID))
                .thenReturn(Optional.of(ingredient));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(actor));
        AtomicLong transactionIds = new AtomicLong(100);
        when(transactionRepository.save(any(StockTransaction.class))).thenAnswer(invocation -> {
            StockTransaction transaction = invocation.getArgument(0);
            transaction.setId(transactionIds.incrementAndGet());
            return transaction;
        });

        service = new InventoryService(
                ingredientRepository,
                batchRepository,
                transactionRepository,
                wasteRecordRepository,
                userRepository,
                clock
        );

        UserPrincipal principal = new UserPrincipal(USER_ID, STORE_ID, actor.getEmail(), Role.STAFF, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void consumeRejectsWhenLockedSellableStockIsInsufficient() {
        InventoryBatch sellable = batch(11L, "CURRENT", "2", TODAY.plusDays(2));
        when(batchRepository.lockSellableFefo(STORE_ID, ingredient.getId(), TODAY))
                .thenReturn(List.of(sellable));

        assertThatThrownBy(() -> service.out(new InventoryOutRequest(ingredient.getId(), new BigDecimal("3"))))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        assertThat(sellable.getQuantity()).isEqualByComparingTo("2");
        verify(transactionRepository, never()).save(any(StockTransaction.class));
        verify(batchRepository, never()).lockPositiveFefo(STORE_ID, ingredient.getId());
    }

    @Test
    void consumeAllocatesLockedBatchesInRepositoryFefoOrderWithoutPartialSuccess() {
        InventoryBatch first = batch(11L, "FIRST", "4", TODAY.plusDays(1));
        InventoryBatch second = batch(12L, "SECOND", "4", TODAY.plusDays(5));
        when(batchRepository.lockSellableFefo(STORE_ID, ingredient.getId(), TODAY))
                .thenReturn(List.of(first, second));

        var response = service.out(new InventoryOutRequest(ingredient.getId(), new BigDecimal("6")));

        assertThat(response).hasSize(2);
        assertThat(response).extracting(item -> item.quantity().stripTrailingZeros().toPlainString())
                .containsExactly("4", "2");
        assertThat(first.getQuantity()).isEqualByComparingTo("0");
        assertThat(second.getQuantity()).isEqualByComparingTo("2");
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(any(StockTransaction.class));
    }

    @Test
    void wasteExportCanDisposeExpiredStockAndCreatesWasteRecord() {
        authenticate(Role.MANAGER);
        InventoryBatch expired = batch(21L, "EXPIRED", "3", TODAY.minusDays(1));
        when(batchRepository.lockPositiveFefo(STORE_ID, ingredient.getId())).thenReturn(List.of(expired));
        var request = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.EXPORT,
                CreateInventoryTransactionRequest.TransactionReason.EXPORT_WASTE,
                "EXPIRED",
                List.of(new CreateInventoryTransactionRequest.Item(
                        ingredient.getId(),
                        null,
                        new BigDecimal("2"),
                        null,
                        null
                ))
        );

        var response = service.createTransaction(request);

        assertThat(response.items()).hasSize(1);
        assertThat(expired.getQuantity()).isEqualByComparingTo("1");
        verify(wasteRecordRepository).save(any(WasteRecord.class));
        verify(batchRepository, never()).lockSellableFefo(STORE_ID, ingredient.getId(), TODAY);
    }

    @ParameterizedTest
    @EnumSource(
            value = CreateInventoryTransactionRequest.TransactionReason.class,
            names = {"EXPORT_WASTE", "EXPORT_ADJUST"}
    )
    void staffCannotRecordWasteOrAdjustInventory(CreateInventoryTransactionRequest.TransactionReason reason) {
        var request = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.EXPORT,
                reason,
                reason == CreateInventoryTransactionRequest.TransactionReason.EXPORT_WASTE ? "EXPIRED" : null,
                List.of(new CreateInventoryTransactionRequest.Item(
                        ingredient.getId(),
                        null,
                        new BigDecimal("1"),
                        null,
                        null
                ))
        );

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                });

        verify(batchRepository, never()).lockPositiveFefo(STORE_ID, ingredient.getId());
        verify(batchRepository, never()).lockSellableFefo(STORE_ID, ingredient.getId(), TODAY);
        verify(transactionRepository, never()).save(any(StockTransaction.class));
    }

    private void authenticate(Role role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, STORE_ID, "staff@example.com", role, false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
    }

    private InventoryBatch batch(Long id, String number, String quantity, LocalDate expiryDate) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(id);
        batch.setStore(ingredient.getStore());
        batch.setIngredient(ingredient);
        batch.setBatchNumber(number);
        batch.setQuantity(new BigDecimal(quantity));
        batch.setExpiryDate(expiryDate);
        batch.setCostPerUnit(new BigDecimal("20000"));
        batch.setReceivedAt(Instant.parse("2026-07-01T00:00:00Z").plusSeconds(id));
        return batch;
    }
}
