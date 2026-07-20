package vn.inventoryai.inventory;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.billing.PlanLimits;
import vn.inventoryai.common.enums.Role;
import vn.inventoryai.common.enums.SubscriptionPlan;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.TenantContext;
import vn.inventoryai.common.security.UserPrincipal;
import vn.inventoryai.inventory.dto.CreateIngredientRequest;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.SubscriptionPlanEntity;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngredientServiceTest {
    private static final long STORE_ID = 42L;

    private IngredientRepository ingredientRepository;
    private InventoryBatchRepository inventoryBatchRepository;
    private StoreRepository storeRepository;
    private TenantSubscriptionRepository subscriptionRepository;
    private PlanEntitlementService entitlementService;
    private IngredientService service;
    private ValidatorFactory validatorFactory;

    @BeforeEach
    void setUp() {
        ingredientRepository = mock(IngredientRepository.class);
        inventoryBatchRepository = mock(InventoryBatchRepository.class);
        storeRepository = mock(StoreRepository.class);
        subscriptionRepository = mock(TenantSubscriptionRepository.class);
        entitlementService = mock(PlanEntitlementService.class);

        Store store = new Store();
        store.setId(STORE_ID);
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        plan.setCode(SubscriptionPlan.BASIC);
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenant(store);
        subscription.setPlan(plan);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(subscriptionRepository.findActiveForUpdate(STORE_ID)).thenReturn(Optional.of(subscription));
        when(entitlementService.limits(SubscriptionPlan.BASIC)).thenReturn(new PlanLimits(1, 10, 50));
        when(ingredientRepository.countByStoreIdAndDeletedFalse(STORE_ID)).thenReturn(0L);
        when(ingredientRepository.findExistingActiveCodes(eq(STORE_ID), anyCollection())).thenReturn(List.of());

        AtomicLong ids = new AtomicLong(100);
        when(ingredientRepository.saveAndFlush(any(Ingredient.class))).thenAnswer(invocation -> {
            Ingredient ingredient = invocation.getArgument(0);
            ingredient.setId(ids.incrementAndGet());
            return ingredient;
        });
        when(ingredientRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<Ingredient> ingredients = invocation.getArgument(0);
            ingredients.forEach(ingredient -> ingredient.setId(ids.incrementAndGet()));
            return ingredients;
        });

        validatorFactory = Validation.buildDefaultValidatorFactory();
        service = new IngredientService(
                ingredientRepository,
                inventoryBatchRepository,
                storeRepository,
                subscriptionRepository,
                entitlementService,
                validatorFactory.getValidator()
        );

        UserPrincipal principal = new UserPrincipal(7L, STORE_ID, "owner@example.com", Role.OWNER, false);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void createLocksActiveSubscriptionBeforeCountingAndWriting() {
        var request = request("MILK", "Sữa");

        var response = service.create(request);

        assertThat(response.code()).isEqualTo("MILK");
        InOrder ordered = inOrder(subscriptionRepository, ingredientRepository);
        ordered.verify(subscriptionRepository).findActiveForUpdate(STORE_ID);
        ordered.verify(ingredientRepository).countByStoreIdAndDeletedFalse(STORE_ID);
        ordered.verify(ingredientRepository).saveAndFlush(any(Ingredient.class));
    }

    @Test
    void createRejectsPlanOverflowWhileHoldingQuotaLock() {
        when(entitlementService.limits(SubscriptionPlan.BASIC)).thenReturn(new PlanLimits(1, 10, 1));
        when(ingredientRepository.countByStoreIdAndDeletedFalse(STORE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request("MILK", "Sữa")))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED));

        verify(subscriptionRepository).findActiveForUpdate(STORE_ID);
        verify(ingredientRepository, never()).saveAndFlush(any(Ingredient.class));
    }

    @Test
    void createRejectsDuplicateNormalizedCodeBeforeWriting() {
        when(ingredientRepository.existsByStoreIdAndCodeAndDeletedFalse(STORE_ID, "MILK"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(" milk ", "Sữa")))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INGREDIENT_CODE_ALREADY_EXISTS));

        verify(ingredientRepository, never()).saveAndFlush(any(Ingredient.class));
    }

    @Test
    void deleteRejectsIngredientWithAnyPositiveStockIncludingExpiredStock() {
        Ingredient ingredient = ingredient("MILK");
        when(ingredientRepository.findActiveByIdAndStoreIdForUpdate(101L, STORE_ID))
                .thenReturn(Optional.of(ingredient));
        when(inventoryBatchRepository.existsByStoreIdAndIngredientIdAndQuantityGreaterThan(
                STORE_ID,
                101L,
                BigDecimal.ZERO
        )).thenReturn(true);

        assertThatThrownBy(() -> service.delete(101L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INGREDIENT_HAS_STOCK));

        assertThat(ingredient.isDeleted()).isFalse();
        verify(ingredientRepository, never()).save(ingredient);
    }

    @Test
    void deleteArchivesIngredientAfterLockWhenStockIsZero() {
        Ingredient ingredient = ingredient("MILK");
        when(ingredientRepository.findActiveByIdAndStoreIdForUpdate(101L, STORE_ID))
                .thenReturn(Optional.of(ingredient));

        service.delete(101L);

        assertThat(ingredient.isDeleted()).isTrue();
        verify(ingredientRepository).save(ingredient);
    }

    @Test
    void importLocksQuotaAndCountsOnlyValidUniqueNewIngredients() {
        String csv = "code,name,unit,category,minStock,maxStock,unitCost\n"
                + "MILK,Sữa,lít,Sữa,1,10,20000\n"
                + "MILK,Sữa trùng,lít,Sữa,1,10,20000\n"
                + "BROKEN,,kg,Khác,0,0,0\n";

        var result = service.importIngredients(csv(csv));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.errors()).hasSize(1);
        ArgumentCaptor<List<Ingredient>> saved = listCaptor();
        InOrder ordered = inOrder(subscriptionRepository, ingredientRepository);
        ordered.verify(subscriptionRepository).findActiveForUpdate(STORE_ID);
        ordered.verify(ingredientRepository).findExistingActiveCodes(eq(STORE_ID), anyCollection());
        ordered.verify(ingredientRepository).countByStoreIdAndDeletedFalse(STORE_ID);
        ordered.verify(ingredientRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(ingredient ->
                assertThat(ingredient.getCode()).isEqualTo("MILK"));
    }

    @Test
    void importRejectsPlanOverflowAfterAcquiringSubscriptionLock() {
        when(entitlementService.limits(SubscriptionPlan.BASIC)).thenReturn(new PlanLimits(1, 10, 1));
        String csv = "code,name,unit\nA,Một,kg\nB,Hai,kg\n";

        assertThatThrownBy(() -> service.importIngredients(csv(csv)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_EXCEEDED));

        verify(subscriptionRepository).findActiveForUpdate(STORE_ID);
        verify(ingredientRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void importRejectsUploadLargerThanCompressedLimitBeforeDatabaseAccess() {
        byte[] oversized = new byte[IngredientService.MAX_UPLOAD_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "too-large.csv", "text/csv", oversized);

        assertValidationFailure(() -> service.importIngredients(file));

        verify(subscriptionRepository, never()).findActiveForUpdate(STORE_ID);
        verify(storeRepository, never()).findById(STORE_ID);
    }

    @Test
    void importRejectsOverlongCsvPhysicalLine() {
        String csv = "code,name,unit\n\"\"" + " ".repeat(IngredientService.MAX_CSV_LINE_CHARACTERS + 1) + "\n";

        assertThatThrownBy(() -> service.importIngredients(csv(csv)))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("16384");
                });

        verify(subscriptionRepository, never()).findActiveForUpdate(STORE_ID);
    }

    @Test
    void importRejectsMoreThanMaximumRows() {
        StringBuilder csv = new StringBuilder("code,name,unit\n");
        for (int index = 0; index <= IngredientService.MAX_IMPORT_ROWS; index++) {
            csv.append("C").append(index).append(",Tên,kg\n");
        }

        assertValidationFailure(() -> service.importIngredients(csv(csv.toString())));

        verify(subscriptionRepository, never()).findActiveForUpdate(STORE_ID);
    }

    @Test
    void importBoundsReturnedRowErrors() {
        StringBuilder csv = new StringBuilder("code,name,unit\n");
        for (int index = 0; index < 150; index++) {
            csv.append("C").append(index).append(",,kg\n");
        }

        var result = service.importIngredients(csv(csv.toString()));

        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isEqualTo(150);
        assertThat(result.errors()).hasSize(IngredientService.MAX_IMPORT_ERRORS);
        assertThat(result.errors().getLast()).contains("giới hạn");
    }

    @Test
    void importAcceptsBoundedInlineStringXlsx() throws Exception {
        String sheet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="inlineStr"><is><t>code</t></is></c>
                      <c r="B1" t="inlineStr"><is><t>name</t></is></c>
                      <c r="C1" t="inlineStr"><is><t>unit</t></is></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="inlineStr"><is><t>RICE</t></is></c>
                      <c r="B2" t="inlineStr"><is><t>Gạo</t></is></c>
                      <c r="C2" t="inlineStr"><is><t>kg</t></is></c>
                    </row>
                  </sheetData>
                </worksheet>
                """;

        var result = service.importIngredients(xlsx(xlsxWithSheet(sheet)));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(subscriptionRepository).findActiveForUpdate(STORE_ID);
        verify(ingredientRepository).saveAllAndFlush(anyList());
    }

    @Test
    void importRejectsXlsxZipBombByInflateRatioAndUncompressedLimits() throws Exception {
        byte[] xlsx = xlsxWithSheet("a".repeat(IngredientService.MAX_UPLOAD_BYTES * 4 + 1));
        assertThat(xlsx.length).isLessThan(IngredientService.MAX_UPLOAD_BYTES);

        assertValidationFailure(() -> service.importIngredients(xlsx(xlsx)));

        verify(subscriptionRepository, never()).findActiveForUpdate(STORE_ID);
    }

    @Test
    void importRejectsXlsxDtdAndExternalEntityPayload() throws Exception {
        String sheet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE worksheet [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <worksheet><sheetData><row><c t="inlineStr"><is><t>&xxe;</t></is></c></row></sheetData></worksheet>
                """;

        assertValidationFailure(() -> service.importIngredients(xlsx(xlsxWithSheet(sheet))));

        verify(subscriptionRepository, never()).findActiveForUpdate(STORE_ID);
    }

    private CreateIngredientRequest request(String code, String name) {
        return new CreateIngredientRequest(
                code,
                name,
                "lít",
                "Sữa",
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("20000")
        );
    }

    private Ingredient ingredient(String code) {
        Ingredient ingredient = new Ingredient();
        Store store = new Store();
        store.setId(STORE_ID);
        ingredient.setId(101L);
        ingredient.setStore(store);
        ingredient.setCode(code);
        ingredient.setName("Sữa");
        ingredient.setUnit("lít");
        ingredient.setMinStock(BigDecimal.ZERO);
        ingredient.setMaxStock(BigDecimal.TEN);
        ingredient.setUnitCost(BigDecimal.ONE);
        return ingredient;
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "ingredients.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile xlsx(byte[] content) {
        return new MockMultipartFile(
                "file",
                "ingredients.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
    }

    private byte[] xlsxWithSheet(String sheet) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<Ingredient>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private void assertValidationFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });
    }
}
