package vn.inventoryai.integration.pos;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;
import vn.inventoryai.common.security.SecurityUtils;
import vn.inventoryai.inventory.InventoryService;
import vn.inventoryai.inventory.dto.CreateInventoryTransactionRequest;
import vn.inventoryai.recipe.Recipe;
import vn.inventoryai.recipe.RecipeRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PosDeductionService {
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 10_000;
    private static final int MAX_WARNINGS = 100;

    private final RecipeRepository recipeRepository;
    private final InventoryService inventoryService;

    @Transactional
    public PosSalesImportResult deductSales(MultipartFile file) {
        validateFile(file);
        Long storeId = SecurityUtils.storeId();
        int rowsParsed = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        Map<Long, BigDecimal> ingredientQuantities = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "File CSV rỗng");
            }
            List<String> headers = parseCsvLine(stripBom(header));
            int codeIndex = indexOf(headers, "code", "productcode", "ma", "mamon");
            int qtyIndex = indexOf(headers, "quantity", "qty", "soluong");
            int revenueIndex = indexOf(headers, "revenue", "doanhthu", "total", "tongtien");

            if (codeIndex < 0 || qtyIndex < 0) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "File CSV phải chứa ít nhất các cột mã món ăn (code/ma) và số lượng bán (quantity/qty)"
                );
            }

            String line;
            int row = 1;
            while ((line = reader.readLine()) != null) {
                row++;
                if (line.isBlank()) continue;
                if (rowsParsed >= MAX_DATA_ROWS) {
                    throw new AppException(
                            ErrorCode.VALIDATION_ERROR,
                            HttpStatus.BAD_REQUEST,
                            "File CSV vượt quá giới hạn " + MAX_DATA_ROWS + " dòng dữ liệu"
                    );
                }

                List<String> values = parseCsvLine(line);
                if (codeIndex >= values.size() || qtyIndex >= values.size()) {
                    addWarning(warnings, "Dòng " + row + " thiếu cột dữ liệu mã hoặc số lượng.");
                    continue;
                }

                String recipeCode = values.get(codeIndex).trim().toUpperCase(Locale.ROOT);
                BigDecimal quantity = parseQuantity(values.get(qtyIndex), row, warnings);
                if (quantity.signum() <= 0) {
                    continue;
                }

                if (revenueIndex >= 0 && revenueIndex < values.size()) {
                    BigDecimal revenue = parseRevenue(values.get(revenueIndex), row, warnings);
                    totalRevenue = totalRevenue.add(revenue);
                }

                // Match with recipe
                Recipe recipe = recipeRepository.findByStoreIdAndCode(storeId, recipeCode).orElse(null);
                if (recipe == null) {
                    addWarning(warnings, "Dòng " + row + ": Không tìm thấy công thức cho món ăn '" + recipeCode + "'.");
                    continue;
                }

                if (!recipe.isActive()) {
                    addWarning(warnings, "Dòng " + row + ": Công thức '" + recipe.getName() + "' đang ngừng hoạt động.");
                    continue;
                }

                // Accumulate ingredients
                recipe.getIngredients().forEach(ri -> {
                    BigDecimal totalNeeded = ri.getQuantity().multiply(quantity);
                    ingredientQuantities.merge(ri.getIngredient().getId(), totalNeeded, BigDecimal::add);
                });

                rowsParsed++;
            }
        } catch (IOException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Không đọc được file CSV");
        }

        if (ingredientQuantities.isEmpty()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Không tìm thấy món ăn nào khớp với công thức hoạt động để thực hiện trừ tồn kho."
            );
        }

        // Trigger stock deduction
        List<CreateInventoryTransactionRequest.Item> items = ingredientQuantities.entrySet().stream()
                .map(entry -> new CreateInventoryTransactionRequest.Item(
                        entry.getKey(),
                        null,
                        entry.getValue(),
                        null,
                        null
                ))
                .toList();

        CreateInventoryTransactionRequest txRequest = new CreateInventoryTransactionRequest(
                CreateInventoryTransactionRequest.TransactionType.EXPORT,
                CreateInventoryTransactionRequest.TransactionReason.EXPORT_CONSUME,
                null,
                items
        );

        // This will run the FEFO deduction and record transactions
        inventoryService.createTransaction(txRequest);

        return new PosSalesImportResult("CSV", "DEDUCT", rowsParsed, totalRevenue, true, List.copyOf(warnings));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "File CSV không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "File CSV vượt quá giới hạn 5 MB");
        }
    }

    private int indexOf(List<String> headers, String... names) {
        for (int i = 0; i < headers.size(); i++) {
            String normalized = headers.get(i).toLowerCase().trim().replace("_", "").replace(" ", "");
            for (String name : names) {
                if (normalized.equals(name)) return i;
            }
        }
        return -1;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quoted) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "File CSV chứa dấu ngoặc kép chưa đóng");
        }
        values.add(current.toString().trim());
        return values;
    }

    private BigDecimal parseQuantity(String rawQty, int row, List<String> warnings) {
        String normalized = rawQty.trim().replace(" ", "").replace(",", "");
        if (normalized.isBlank()) {
            addWarning(warnings, "Dòng " + row + " có số lượng bán rỗng.");
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal qty = new BigDecimal(normalized);
            if (qty.signum() <= 0) {
                addWarning(warnings, "Dòng " + row + " có số lượng bán nhỏ hơn hoặc bằng 0.");
                return BigDecimal.ZERO;
            }
            return qty;
        } catch (NumberFormatException ex) {
            addWarning(warnings, "Dòng " + row + " có số lượng không hợp lệ.");
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseRevenue(String rawRevenue, int row, List<String> warnings) {
        String normalized = rawRevenue.trim().replace(" ", "").replace(",", "").replace("₫", "");
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal revenue = new BigDecimal(normalized);
            if (revenue.signum() < 0) {
                return BigDecimal.ZERO;
            }
            return revenue;
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(warning);
        } else if (warnings.size() == MAX_WARNINGS) {
            warnings.add("Các cảnh báo còn lại đã được lược bỏ.");
        }
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
