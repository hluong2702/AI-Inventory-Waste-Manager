package vn.inventoryai.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.billing.PlanEntitlementService;
import vn.inventoryai.inventory.dto.CreateIngredientRequest;
import vn.inventoryai.inventory.dto.IngredientImportResult;
import vn.inventoryai.inventory.dto.IngredientResponse;
import vn.inventoryai.store.Store;
import vn.inventoryai.store.StoreRepository;
import vn.inventoryai.store.Subscription;
import vn.inventoryai.store.SubscriptionRepository;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class IngredientService {
    private final IngredientRepository ingredientRepository;
    private final StoreRepository storeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanEntitlementService planEntitlementService;

    @Transactional(readOnly = true)
    public List<IngredientResponse> list() {
        Long storeId = SecurityUtils.storeId();
        return ingredientRepository.findByStoreIdAndDeletedFalse(storeId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public IngredientResponse create(CreateIngredientRequest request) {
        Long storeId = SecurityUtils.storeId();
        enforceIngredientLimit(storeId, 1);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        Ingredient ingredient = buildIngredient(store, request);
        return toResponse(ingredientRepository.save(ingredient));
    }

    @Transactional
    public IngredientResponse update(Long id, CreateIngredientRequest request) {
        Long storeId = SecurityUtils.storeId();
        Ingredient ingredient = ingredientRepository.findById(id)
                .filter(item -> item.getStore().getId().equals(storeId))
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found"));

        ingredient.setName(request.name());
        ingredient.setUnit(request.unit());
        ingredient.setCategory(blankToDefault(request.category(), "Chưa phân loại"));
        ingredient.setMinStock(request.minStock());
        ingredient.setMaxStock(request.maxStock() == null ? BigDecimal.ZERO : request.maxStock());
        ingredient.setUnitCost(request.unitCost() == null ? ingredient.getUnitCost() : request.unitCost());
        return toResponse(ingredientRepository.save(ingredient));
    }

    @Transactional
    public void delete(Long id) {
        Long storeId = SecurityUtils.storeId();
        Ingredient ingredient = ingredientRepository.findById(id)
                .filter(item -> item.getStore().getId().equals(storeId))
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Ingredient not found"));
        ingredient.setDeleted(true);
        ingredientRepository.save(ingredient);
    }

    @Transactional
    public IngredientImportResult importIngredients(MultipartFile file) {
        Long storeId = SecurityUtils.storeId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Store not found"));
        List<Map<String, String>> rows = readRows(file);
        enforceIngredientLimit(storeId, rows.size());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            try {
                CreateIngredientRequest request = new CreateIngredientRequest(
                        value(row, "code", "ma", "mã"),
                        required(row, "name", "ten", "tên", "nguyenlieu", "nguyên liệu"),
                        required(row, "unit", "donvi", "đơn vị"),
                        value(row, "category", "danhmuc", "danh mục"),
                        decimal(row, BigDecimal.ZERO, "minstock", "min_stock", "tonmin", "tồn min"),
                        decimal(row, BigDecimal.ZERO, "maxstock", "max_stock", "tonmax", "tồn max"),
                        decimal(row, BigDecimal.ZERO, "unitcost", "unit_cost", "dongia", "đơn giá")
                );
                String code = normalizeCode(request.code(), request.name());
                if (ingredientRepository.findByStoreIdAndCodeAndDeletedFalse(storeId, code).isPresent()) {
                    skipped++;
                    continue;
                }
                ingredientRepository.save(buildIngredient(store, request));
                imported++;
            } catch (RuntimeException ex) {
                errors.add("Dòng " + rowNumber + ": " + ex.getMessage());
            }
        }
        return new IngredientImportResult(imported, skipped, errors);
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

    private void enforceIngredientLimit(Long storeId, int requestedNewRows) {
        Subscription subscription = subscriptionRepository.findByStoreId(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Subscription not found"));
        Integer maxIngredients = planEntitlementService.limits(subscription.getPlan()).ingredients();
        if (maxIngredients != null) {
            long current = ingredientRepository.countByStoreIdAndDeletedFalse(storeId);
            if (current + requestedNewRows > maxIngredients) {
                throw new AppException(ErrorCode.PLAN_LIMIT_EXCEEDED, HttpStatus.CONFLICT, "Ingredient limit exceeded for current plan");
            }
        }
    }

    private Ingredient buildIngredient(Store store, CreateIngredientRequest request) {
        Ingredient ingredient = new Ingredient();
        ingredient.setStore(store);
        ingredient.setCode(normalizeCode(request.code(), request.name()));
        ingredient.setName(request.name());
        ingredient.setUnit(request.unit());
        ingredient.setCategory(blankToDefault(request.category(), "Chưa phân loại"));
        ingredient.setMinStock(request.minStock());
        ingredient.setMaxStock(request.maxStock() == null ? BigDecimal.ZERO : request.maxStock());
        ingredient.setUnitCost(request.unitCost() == null ? BigDecimal.ZERO : request.unitCost());
        return ingredient;
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
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (filename.endsWith(".xlsx")) {
                return readXlsxRows(file.getBytes());
            }
            return readCsvRows(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Không đọc được file import: " + ex.getMessage());
        }
    }

    private List<Map<String, String>> readCsvRows(String content) throws Exception {
        List<List<String>> rawRows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) rawRows.add(parseCsvLine(line));
            }
        }
        return mapRows(rawRows);
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private List<Map<String, String>> readXlsxRows(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("xl/sharedStrings.xml") || entry.getName().equals("xl/worksheets/sheet1.xml")) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) throw new IllegalArgumentException("Không tìm thấy sheet1 trong file XLSX");

        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(sheet));
        var rows = document.getElementsByTagName("row");
        List<List<String>> rawRows = new ArrayList<>();
        for (int i = 0; i < rows.getLength(); i++) {
            var rowNode = rows.item(i);
            var cells = ((org.w3c.dom.Element) rowNode).getElementsByTagName("c");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < cells.getLength(); j++) {
                var cell = (org.w3c.dom.Element) cells.item(j);
                String type = cell.getAttribute("t");
                var valueNodes = cell.getElementsByTagName("v");
                String value = valueNodes.getLength() == 0 ? "" : valueNodes.item(0).getTextContent();
                if ("s".equals(type) && !value.isBlank()) {
                    value = sharedStrings.get(Integer.parseInt(value));
                }
                values.add(value.trim());
            }
            if (!values.isEmpty()) rawRows.add(values);
        }
        return mapRows(rawRows);
    }

    private List<String> parseSharedStrings(byte[] xml) throws Exception {
        if (xml == null) return List.of();
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var nodes = document.getElementsByTagName("t");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent());
        }
        return values;
    }

    private List<Map<String, String>> mapRows(List<List<String>> rawRows) {
        if (rawRows.isEmpty()) return List.of();
        List<String> headers = rawRows.get(0).stream().map(this::normalizeHeader).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            Map<String, String> row = new LinkedHashMap<>();
            List<String> values = rawRows.get(i);
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < values.size() ? values.get(j) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace(" ", "").replace("_", "");
    }

    private String required(Map<String, String> row, String... keys) {
        String value = value(row, keys);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Thiếu dữ liệu bắt buộc");
        return value;
    }

    private String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private BigDecimal decimal(Map<String, String> row, BigDecimal fallback, String... keys) {
        String value = value(row, keys);
        return value.isBlank() ? fallback : new BigDecimal(value.replace(",", ""));
    }

    private String normalizeCode(String code, String name) {
        if (code != null && !code.isBlank()) return code.trim().toUpperCase();
        String slug = name == null ? "ITEM" : name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "ING-" + (slug.isBlank() ? "ITEM" : slug);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
