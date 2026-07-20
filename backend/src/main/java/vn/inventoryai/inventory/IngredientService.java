package vn.inventoryai.inventory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.dto.CreateIngredientRequest;
import vn.inventoryai.inventory.dto.IngredientImportResult;
import vn.inventoryai.inventory.dto.IngredientResponse;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.subscription.TenantSubscription;
import vn.inventoryai.subscription.TenantSubscriptionRepository;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class IngredientService {
    static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    static final int MAX_IMPORT_ROWS = 2_000;
    static final int MAX_COLUMNS = 32;
    static final int MAX_CELL_CHARACTERS = 512;
    static final int MAX_CSV_LINE_CHARACTERS = 16 * 1024;
    static final int MAX_IMPORT_ERRORS = 100;

    private static final int MAX_ZIP_ENTRIES = 256;
    private static final long MAX_XLSX_UNCOMPRESSED_BYTES = 16L * 1024 * 1024;
    private static final int MAX_RELEVANT_XLSX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final double MIN_ZIP_INFLATE_RATIO = 0.01d;
    private static final String SHARED_STRINGS_ENTRY = "xl/sharedStrings.xml";
    private static final String FIRST_SHEET_ENTRY = "xl/worksheets/sheet1.xml";

    private final IngredientRepository ingredientRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final StoreRepository storeRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final PlanEntitlementService planEntitlementService;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<IngredientResponse> list() {
        Long storeId = SecurityUtils.storeId();
        return ingredientRepository.findByStoreIdAndDeletedFalse(storeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public IngredientResponse create(CreateIngredientRequest request) {
        Long storeId = SecurityUtils.storeId();
        TenantSubscription lockedSubscription = lockActiveSubscription(storeId);
        enforceIngredientLimit(storeId, lockedSubscription, 1);

        String normalizedCode = normalizeCode(request.code(), request.name());
        assertCodeAvailable(storeId, normalizedCode, null);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        Ingredient ingredient = buildIngredient(store, request, normalizedCode);
        try {
            return toResponse(ingredientRepository.saveAndFlush(ingredient));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCode(normalizedCode);
        }
    }

    @Transactional
    public IngredientResponse update(Long id, CreateIngredientRequest request) {
        Long storeId = SecurityUtils.storeId();
        Ingredient ingredient = ingredientRepository.findById(id)
                .filter(item -> item.getStore().getId().equals(storeId))
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found"));

        String normalizedCode = normalizeCode(request.code(), request.name());
        assertCodeAvailable(storeId, normalizedCode, ingredient.getId());
        ingredient.setCode(normalizedCode);
        ingredient.setName(request.name());
        ingredient.setUnit(request.unit());
        ingredient.setCategory(blankToDefault(request.category(), "Chưa phân loại"));
        ingredient.setMinStock(request.minStock());
        ingredient.setMaxStock(request.maxStock() == null ? BigDecimal.ZERO : request.maxStock());
        ingredient.setUnitCost(request.unitCost() == null ? ingredient.getUnitCost() : request.unitCost());
        try {
            return toResponse(ingredientRepository.saveAndFlush(ingredient));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateCode(normalizedCode);
        }
    }

    @Transactional
    public void delete(Long id) {
        Long storeId = SecurityUtils.storeId();
        Ingredient ingredient = ingredientRepository.findActiveByIdAndStoreIdForUpdate(id, storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found"));
        if (inventoryBatchRepository.existsByStoreIdAndIngredientIdAndQuantityGreaterThan(
                storeId,
                ingredient.getId(),
                BigDecimal.ZERO
        )) {
            throw new AppException(
                    ErrorCode.INGREDIENT_HAS_STOCK,
                    HttpStatus.CONFLICT,
                    "Không thể lưu trữ nguyên liệu vẫn còn tồn kho; hãy xuất, hủy hoặc điều chỉnh tồn về 0 trước"
            );
        }
        ingredient.setDeleted(true);
        ingredientRepository.save(ingredient);
    }

    @Transactional
    public IngredientImportResult importIngredients(MultipartFile file) {
        Long storeId = SecurityUtils.storeId();
        List<Map<String, String>> rows = readRows(file);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));

        ImportPreparation preparation = prepareImport(rows);
        TenantSubscription lockedSubscription = lockActiveSubscription(storeId);

        Set<String> existingCodes = preparation.candidates().isEmpty()
                ? Set.of()
                : new HashSet<>(ingredientRepository.findExistingActiveCodes(
                        storeId,
                        preparation.candidates().stream().map(ImportCandidate::code).toList()
                ));
        List<ImportCandidate> newCandidates = preparation.candidates().stream()
                .filter(candidate -> !existingCodes.contains(candidate.code()))
                .toList();

        enforceIngredientLimit(storeId, lockedSubscription, newCandidates.size());
        if (!newCandidates.isEmpty()) {
            try {
                ingredientRepository.saveAllAndFlush(newCandidates.stream()
                        .map(candidate -> buildIngredient(store, candidate.request(), candidate.code()))
                        .toList());
            } catch (DataIntegrityViolationException ex) {
                throw new AppException(
                        ErrorCode.INGREDIENT_CODE_ALREADY_EXISTS,
                        HttpStatus.CONFLICT,
                        "Một hoặc nhiều mã nguyên liệu đã được tạo đồng thời; vui lòng tải lại và thử lại"
                );
            }
        }

        int databaseDuplicates = preparation.candidates().size() - newCandidates.size();
        return new IngredientImportResult(
                newCandidates.size(),
                preparation.skipped() + databaseDuplicates,
                List.copyOf(preparation.errors())
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<String> exportIngredientTemplate() {
        String csv = "code,name,unit,category,minStock,maxStock,unitCost\n"
                + "ING-MILK,Sữa tươi không đường,hộp,Sữa,20,100,28000\n"
                + "ING-PEARL,Trân châu đen,kg,Topping,5,30,35000\n";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=ingredient-import-template.csv")
                .body(csv);
    }

    private TenantSubscription lockActiveSubscription(Long storeId) {
        return tenantSubscriptionRepository.findActiveForUpdate(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
    }

    private void enforceIngredientLimit(Long storeId, TenantSubscription lockedSubscription, int requestedNewRows) {
        Integer maxIngredients = planEntitlementService.limits(lockedSubscription.getPlan().getCode()).ingredients();
        if (maxIngredients == null) {
            return;
        }
        long current = ingredientRepository.countByStoreIdAndDeletedFalse(storeId);
        if (current + requestedNewRows > maxIngredients) {
            throw new AppException(
                    ErrorCode.PLAN_LIMIT_EXCEEDED,
                    HttpStatus.CONFLICT,
                    "Ingredient limit exceeded for current plan"
            );
        }
    }

    private ImportPreparation prepareImport(List<Map<String, String>> rows) {
        List<ImportCandidate> candidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        int skipped = 0;

        for (int index = 0; index < rows.size(); index++) {
            int rowNumber = index + 2;
            try {
                Map<String, String> row = rows.get(index);
                String name = required(row, "name", "ten", "tên", "nguyenlieu", "nguyên liệu");
                String code = normalizeCode(value(row, "code", "ma", "mã"), name);
                CreateIngredientRequest request = new CreateIngredientRequest(
                        code,
                        name,
                        required(row, "unit", "donvi", "đơn vị"),
                        value(row, "category", "danhmuc", "danh mục"),
                        decimal(row, BigDecimal.ZERO, "minstock", "min_stock", "tonmin", "tồn min"),
                        decimal(row, BigDecimal.ZERO, "maxstock", "max_stock", "tonmax", "tồn max"),
                        decimal(row, BigDecimal.ZERO, "unitcost", "unit_cost", "dongia", "đơn giá")
                );
                validateImportedRequest(request);
                if (!codesInFile.add(code)) {
                    skipped++;
                    continue;
                }
                candidates.add(new ImportCandidate(request, code));
            } catch (IllegalArgumentException ex) {
                skipped++;
                addBoundedError(errors, rowNumber, ex.getMessage());
            }
        }
        return new ImportPreparation(List.copyOf(candidates), skipped, List.copyOf(errors));
    }

    private void validateImportedRequest(CreateIngredientRequest request) {
        Set<ConstraintViolation<CreateIngredientRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .sorted((left, right) -> left.getPropertyPath().toString()
                            .compareTo(right.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Dữ liệu không hợp lệ");
            throw new IllegalArgumentException(message);
        }
    }

    private void addBoundedError(List<String> errors, int rowNumber, String message) {
        if (errors.size() < MAX_IMPORT_ERRORS - 1) {
            String safeMessage = message == null || message.isBlank() ? "Dữ liệu không hợp lệ" : message;
            safeMessage = safeMessage.replace('\r', ' ').replace('\n', ' ');
            if (safeMessage.length() > 180) {
                safeMessage = safeMessage.substring(0, 180);
            }
            errors.add("Dòng " + rowNumber + ": " + safeMessage);
        } else if (errors.size() == MAX_IMPORT_ERRORS - 1) {
            errors.add("Danh sách lỗi đã được giới hạn; hãy sửa file và thử lại");
        }
    }

    private Ingredient buildIngredient(Store store, CreateIngredientRequest request, String normalizedCode) {
        Ingredient ingredient = new Ingredient();
        ingredient.setStore(store);
        ingredient.setCode(normalizedCode);
        ingredient.setName(request.name());
        ingredient.setUnit(request.unit());
        ingredient.setCategory(blankToDefault(request.category(), "Chưa phân loại"));
        ingredient.setMinStock(request.minStock());
        ingredient.setMaxStock(request.maxStock() == null ? BigDecimal.ZERO : request.maxStock());
        ingredient.setUnitCost(request.unitCost() == null ? BigDecimal.ZERO : request.unitCost());
        return ingredient;
    }

    private void assertCodeAvailable(Long storeId, String code, Long excludedIngredientId) {
        boolean exists = excludedIngredientId == null
                ? ingredientRepository.existsByStoreIdAndCodeAndDeletedFalse(storeId, code)
                : ingredientRepository.existsByStoreIdAndCodeAndDeletedFalseAndIdNot(
                        storeId,
                        code,
                        excludedIngredientId
                );
        if (exists) {
            throw duplicateCode(code);
        }
    }

    private AppException duplicateCode(String code) {
        return new AppException(
                ErrorCode.INGREDIENT_CODE_ALREADY_EXISTS,
                HttpStatus.CONFLICT,
                "Mã nguyên liệu '" + code + "' đã tồn tại trong cửa hàng"
        );
    }

    private IngredientResponse toResponse(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getStore().getId(),
                ingredient.getCode(),
                ingredient.getName(),
                ingredient.getUnit(),
                ingredient.getCategory(),
                ingredient.getMinStock(),
                ingredient.getMaxStock(),
                ingredient.getUnitCost(),
                !ingredient.isDeleted()
        );
    }

    private List<Map<String, String>> readRows(MultipartFile file) {
        byte[] bytes = readBoundedUpload(file);
        String filename = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".xlsx")) {
                if (!hasZipMagic(bytes)) {
                    throw invalidImport("File XLSX không có định dạng ZIP hợp lệ");
                }
                return mapRows(readXlsxRows(bytes));
            }
            if (filename.endsWith(".csv")) {
                if (hasZipMagic(bytes)) {
                    throw invalidImport("Nội dung file không khớp phần mở rộng CSV");
                }
                return mapRows(readCsvRows(bytes));
            }
            throw invalidImport("Chỉ chấp nhận file CSV hoặc XLSX");
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidImport("Không đọc được file import do định dạng không hợp lệ");
        }
    }

    private byte[] readBoundedUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidImport("File import không được để trống");
        }
        if (file.getSize() < 0 || file.getSize() > MAX_UPLOAD_BYTES) {
            throw invalidImport("File import vượt quá giới hạn 2 MB");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_UPLOAD_BYTES + 1);
            if (bytes.length > MAX_UPLOAD_BYTES || input.read() != -1) {
                throw invalidImport("File import vượt quá giới hạn 2 MB");
            }
            return bytes;
        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalidImport("Không đọc được file import");
        }
    }

    private boolean hasZipMagic(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
                && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
    }

    private List<List<String>> readCsvRows(byte[] bytes) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean afterClosingQuote = false;
        boolean previousCarriageReturn = false;
        int lineCharacters = 0;

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), decoder)) {
            int next;
            while ((next = reader.read()) != -1) {
                char value = (char) next;
                if (previousCarriageReturn && value == '\n') {
                    previousCarriageReturn = false;
                    lineCharacters = 0;
                    continue;
                }
                previousCarriageReturn = false;

                if (value == '\r' || value == '\n') {
                    lineCharacters = 0;
                    if (inQuotes) {
                        appendCellCharacter(cell, '\n');
                        previousCarriageReturn = value == '\r';
                        continue;
                    }
                    finishCsvCell(row, cell);
                    addCsvRow(rows, row);
                    row = new ArrayList<>();
                    cell = new StringBuilder();
                    afterClosingQuote = false;
                    previousCarriageReturn = value == '\r';
                    continue;
                }

                lineCharacters++;
                if (lineCharacters > MAX_CSV_LINE_CHARACTERS) {
                    throw invalidImport("Một dòng CSV vượt quá giới hạn 16384 ký tự");
                }

                if (inQuotes) {
                    if (value == '"') {
                        inQuotes = false;
                        afterClosingQuote = true;
                    } else {
                        appendCellCharacter(cell, value);
                    }
                    continue;
                }

                if (afterClosingQuote) {
                    if (value == '"') {
                        appendCellCharacter(cell, '"');
                        inQuotes = true;
                        afterClosingQuote = false;
                    } else if (value == ',') {
                        finishCsvCell(row, cell);
                        cell = new StringBuilder();
                        afterClosingQuote = false;
                    } else if (!Character.isWhitespace(value)) {
                        throw invalidImport("CSV có ký tự không hợp lệ sau dấu ngoặc kép");
                    }
                    continue;
                }

                if (value == ',') {
                    finishCsvCell(row, cell);
                    cell = new StringBuilder();
                } else if (value == '"') {
                    if (!cell.isEmpty()) {
                        throw invalidImport("CSV có dấu ngoặc kép không hợp lệ");
                    }
                    inQuotes = true;
                } else {
                    appendCellCharacter(cell, value);
                }
            }
        }

        if (inQuotes) {
            throw invalidImport("CSV có trường được trích dẫn chưa đóng");
        }
        if (!row.isEmpty() || !cell.isEmpty() || afterClosingQuote) {
            finishCsvCell(row, cell);
            addCsvRow(rows, row);
        }
        return rows;
    }

    private void appendCellCharacter(StringBuilder cell, char value) {
        if (cell.length() >= MAX_CELL_CHARACTERS) {
            throw invalidImport("Một ô dữ liệu vượt quá giới hạn 512 ký tự");
        }
        cell.append(value);
    }

    private void finishCsvCell(List<String> row, StringBuilder cell) {
        if (row.size() >= MAX_COLUMNS) {
            throw invalidImport("Một dòng import vượt quá giới hạn 32 cột");
        }
        row.add(cell.toString());
    }

    private void addCsvRow(List<List<String>> rows, List<String> row) {
        if (row.stream().allMatch(String::isBlank)) {
            return;
        }
        if (rows.size() >= MAX_IMPORT_ROWS + 1) {
            throw invalidImport("File import vượt quá giới hạn 2000 dòng dữ liệu");
        }
        rows.add(List.copyOf(row));
    }

    private List<List<String>> readXlsxRows(byte[] bytes) throws IOException, XMLStreamException {
        Path temporaryFile = Files.createTempFile("ingredient-import-", ".xlsx");
        try {
            Files.write(temporaryFile, bytes);
            try (ZipFile zip = new ZipFile(temporaryFile.toFile(), StandardCharsets.UTF_8)) {
                validateZipArchive(zip);
                ZipEntry sheetEntry = zip.getEntry(FIRST_SHEET_ENTRY);
                if (sheetEntry == null) {
                    throw invalidImport("Không tìm thấy sheet đầu tiên trong file XLSX");
                }
                ZipEntry sharedStringsEntry = zip.getEntry(SHARED_STRINGS_ENTRY);
                List<String> sharedStrings = sharedStringsEntry == null
                        ? List.of()
                        : parseSharedStrings(readZipEntry(zip, sharedStringsEntry));
                return parseSheet(readZipEntry(zip, sheetEntry), sharedStrings);
            }
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                temporaryFile.toFile().deleteOnExit();
            }
        }
    }

    private void validateZipArchive(ZipFile zip) {
        int entryCount = 0;
        long totalUncompressed = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            entryCount++;
            if (entryCount > MAX_ZIP_ENTRIES) {
                throw invalidImport("File XLSX chứa quá nhiều thành phần");
            }
            String name = entry.getName();
            if (name.length() > 256 || name.startsWith("/") || name.contains("\\") || name.contains("../")) {
                throw invalidImport("File XLSX chứa đường dẫn không hợp lệ");
            }
            if (entry.isDirectory()) {
                continue;
            }
            long size = entry.getSize();
            long compressedSize = entry.getCompressedSize();
            if (size < 0 || compressedSize < 0) {
                throw invalidImport("File XLSX thiếu thông tin kích thước an toàn");
            }
            if (size > MAX_XLSX_UNCOMPRESSED_BYTES
                    || totalUncompressed > MAX_XLSX_UNCOMPRESSED_BYTES - size) {
                throw invalidImport("Nội dung giải nén XLSX vượt quá giới hạn 16 MB");
            }
            totalUncompressed += size;
            if (size >= 4_096 && (compressedSize == 0
                    || (double) compressedSize / (double) size < MIN_ZIP_INFLATE_RATIO)) {
                throw invalidImport("File XLSX có tỷ lệ nén không an toàn");
            }
        }
    }

    private byte[] readZipEntry(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > MAX_RELEVANT_XLSX_ENTRY_BYTES) {
            throw invalidImport("Một thành phần XLSX vượt quá giới hạn 8 MB");
        }
        try (InputStream input = zip.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) entry.getSize())) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (total > MAX_RELEVANT_XLSX_ENTRY_BYTES - read) {
                    throw invalidImport("Một thành phần XLSX vượt quá giới hạn 8 MB");
                }
                output.write(buffer, 0, read);
                total += read;
            }
            if (total != entry.getSize()) {
                throw invalidImport("Kích thước thành phần XLSX không nhất quán");
            }
            return output.toByteArray();
        }
    }

    private List<String> parseSharedStrings(byte[] xml) throws XMLStreamException {
        List<String> values = new ArrayList<>();
        XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
        StringBuilder current = null;
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                rejectUnsafeXmlEvent(event);
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("si".equals(name)) {
                        current = new StringBuilder();
                    } else if ("t".equals(name) && current != null) {
                        inText = true;
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && inText && current != null) {
                    appendLimitedText(current, reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("t".equals(name)) {
                        inText = false;
                    } else if ("si".equals(name) && current != null) {
                        if (values.size() >= MAX_IMPORT_ROWS * MAX_COLUMNS) {
                            throw invalidImport("Bảng chuỗi dùng chung trong XLSX quá lớn");
                        }
                        values.add(current.toString());
                        current = null;
                    }
                }
            }
            return List.copyOf(values);
        } finally {
            reader.close();
        }
    }

    private List<List<String>> parseSheet(byte[] xml, List<String> sharedStrings) throws XMLStreamException {
        List<List<String>> rows = new ArrayList<>();
        XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
        List<String> currentRow = null;
        Set<Integer> occupiedColumns = null;
        StringBuilder currentCell = null;
        String currentCellType = null;
        int currentColumn = -1;
        int nextColumn = 0;
        int physicalCells = 0;
        boolean captureValue = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                rejectUnsafeXmlEvent(event);
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("row".equals(name)) {
                        if (currentRow != null) {
                            throw invalidImport("Cấu trúc hàng XLSX không hợp lệ");
                        }
                        currentRow = new ArrayList<>();
                        occupiedColumns = new HashSet<>();
                        nextColumn = 0;
                        physicalCells = 0;
                    } else if ("c".equals(name) && currentRow != null) {
                        physicalCells++;
                        if (physicalCells > MAX_COLUMNS) {
                            throw invalidImport("Một dòng import vượt quá giới hạn 32 ô");
                        }
                        String reference = reader.getAttributeValue(null, "r");
                        currentColumn = reference == null || reference.isBlank()
                                ? nextColumn
                                : columnIndex(reference);
                        if (currentColumn >= MAX_COLUMNS || !occupiedColumns.add(currentColumn)) {
                            throw invalidImport("Vị trí ô XLSX không hợp lệ hoặc vượt quá giới hạn 32 cột");
                        }
                        nextColumn = currentColumn + 1;
                        currentCellType = reader.getAttributeValue(null, "t");
                        currentCell = new StringBuilder();
                    } else if (currentCell != null && ("v".equals(name)
                            || ("t".equals(name) && "inlineStr".equals(currentCellType)))) {
                        captureValue = true;
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && captureValue && currentCell != null) {
                    appendLimitedText(currentCell, reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("v".equals(name) || "t".equals(name)) {
                        captureValue = false;
                    } else if ("c".equals(name) && currentRow != null && currentCell != null) {
                        setCellValue(currentRow, currentColumn, resolveCellValue(currentCell.toString(), currentCellType, sharedStrings));
                        currentCell = null;
                        currentCellType = null;
                        currentColumn = -1;
                        captureValue = false;
                    } else if ("row".equals(name) && currentRow != null) {
                        if (currentRow.stream().anyMatch(value -> !value.isBlank())) {
                            if (rows.size() >= MAX_IMPORT_ROWS + 1) {
                                throw invalidImport("File import vượt quá giới hạn 2000 dòng dữ liệu");
                            }
                            rows.add(List.copyOf(currentRow));
                        }
                        currentRow = null;
                        occupiedColumns = null;
                    }
                }
            }
            return rows;
        } finally {
            reader.close();
        }
    }

    private XMLInputFactory secureXmlFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException ignored) {
            // The mandatory DTD and external-entity switches above remain enforced.
        }
        return factory;
    }

    private void rejectUnsafeXmlEvent(int event) {
        if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
            throw invalidImport("File XLSX chứa thực thể XML không được phép");
        }
    }

    private void appendLimitedText(StringBuilder target, String value) {
        if (value != null && target.length() > MAX_CELL_CHARACTERS - value.length()) {
            throw invalidImport("Một ô dữ liệu vượt quá giới hạn 512 ký tự");
        }
        if (value != null) {
            target.append(value);
        }
    }

    private int columnIndex(String reference) {
        int column = 0;
        int letters = 0;
        while (letters < reference.length() && Character.isLetter(reference.charAt(letters))) {
            char value = Character.toUpperCase(reference.charAt(letters));
            if (value < 'A' || value > 'Z') {
                throw invalidImport("Tham chiếu ô XLSX không hợp lệ");
            }
            column = Math.multiplyExact(column, 26) + (value - 'A' + 1);
            if (column > MAX_COLUMNS) {
                throw invalidImport("Một dòng import vượt quá giới hạn 32 cột");
            }
            letters++;
        }
        if (letters == 0 || letters == reference.length()) {
            throw invalidImport("Tham chiếu ô XLSX không hợp lệ");
        }
        for (int index = letters; index < reference.length(); index++) {
            if (!Character.isDigit(reference.charAt(index))) {
                throw invalidImport("Tham chiếu ô XLSX không hợp lệ");
            }
        }
        return column - 1;
    }

    private String resolveCellValue(String raw, String type, List<String> sharedStrings) {
        if (!"s".equals(type)) {
            return raw.trim();
        }
        try {
            int index = Integer.parseInt(raw.trim());
            if (index < 0 || index >= sharedStrings.size()) {
                throw invalidImport("Chỉ mục chuỗi dùng chung trong XLSX không hợp lệ");
            }
            return sharedStrings.get(index).trim();
        } catch (NumberFormatException ex) {
            throw invalidImport("Chỉ mục chuỗi dùng chung trong XLSX không hợp lệ");
        }
    }

    private void setCellValue(List<String> row, int column, String value) {
        while (row.size() <= column) {
            row.add("");
        }
        row.set(column, value);
    }

    private List<Map<String, String>> mapRows(List<List<String>> rawRows) {
        if (rawRows.isEmpty()) {
            return List.of();
        }
        List<String> headers = rawRows.getFirst().stream().map(this::normalizeHeader).toList();
        Set<String> uniqueHeaders = new HashSet<>();
        for (String header : headers) {
            if (!header.isBlank() && !uniqueHeaders.add(header)) {
                throw invalidImport("File import có tên cột bị trùng lặp");
            }
        }
        if (!containsAny(headers, "name", "ten", "tên", "nguyenlieu", "nguyên liệu")
                || !containsAny(headers, "unit", "donvi", "đơn vị")) {
            throw invalidImport("File import phải có cột name và unit");
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < rawRows.size(); index++) {
            Map<String, String> row = new LinkedHashMap<>();
            List<String> values = rawRows.get(index);
            for (int column = 0; column < headers.size(); column++) {
                String header = headers.get(column);
                if (!header.isBlank()) {
                    row.put(header, column < values.size() ? values.get(column).trim() : "");
                }
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private boolean containsAny(Collection<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(normalizeHeader(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHeader(String value) {
        return value == null
                ? ""
                : value.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "");
    }

    private String required(Map<String, String> row, String... keys) {
        String value = value(row, keys);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Thiếu dữ liệu bắt buộc");
        }
        return value;
    }

    private String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private BigDecimal decimal(Map<String, String> row, BigDecimal fallback, String... keys) {
        String value = value(row, keys);
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Giá trị số không hợp lệ");
        }
    }

    private String normalizeCode(String code, String name) {
        String normalized;
        if (code != null && !code.isBlank()) {
            normalized = code.trim().toUpperCase(Locale.ROOT);
        } else {
            String slug = name == null
                    ? "ITEM"
                    : name.trim()
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "-")
                    .replaceAll("(^-|-$)", "");
            normalized = "ING-" + (slug.isBlank() ? "ITEM" : slug);
        }
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80).replaceAll("-+$", "");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private AppException invalidImport(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    private record ImportCandidate(CreateIngredientRequest request, String code) {
    }

    private record ImportPreparation(List<ImportCandidate> candidates, int skipped, List<String> errors) {
    }
}
