package vn.inventoryai.integration.pos;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvPosSalesImportService implements PosSalesImportService {
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 10_000;
    private static final int MAX_WARNINGS = 100;

    @Override
    public String provider() {
        return "CSV";
    }

    @Override
    public PosSalesImportResult previewSales(MultipartFile file) {
        validateFile(file);
        int rowsParsed = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw invalidFile("File CSV rỗng");
            }
            List<String> headers = parseCsvLine(stripBom(header));
            int revenueIndex = indexOf(headers, "revenue", "doanhthu", "total", "tongtien");
            if (revenueIndex < 0) {
                addWarning(warnings, "Không tìm thấy cột doanh thu; tổng doanh thu xem trước được trả về là 0.");
            }
            String line;
            int row = 1;
            while ((line = reader.readLine()) != null) {
                row++;
                if (line.isBlank()) continue;
                if (rowsParsed >= MAX_DATA_ROWS) {
                    throw invalidFile("File CSV vượt quá giới hạn " + MAX_DATA_ROWS + " dòng dữ liệu");
                }
                List<String> values = parseCsvLine(line);
                if (revenueIndex >= 0) {
                    if (revenueIndex >= values.size()) {
                        addWarning(warnings, "Dòng " + row + " thiếu cột doanh thu.");
                    } else {
                        BigDecimal revenue = parseRevenue(values.get(revenueIndex), row, warnings);
                        totalRevenue = totalRevenue.add(revenue);
                    }
                }
                rowsParsed++;
            }
        } catch (IOException ex) {
            throw invalidFile("Không đọc được file CSV");
        }
        return new PosSalesImportResult(provider(), "PREVIEW_ONLY", rowsParsed, totalRevenue, false, List.copyOf(warnings));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile("File CSV không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw invalidFile("File CSV vượt quá giới hạn 5 MB");
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
            throw invalidFile("File CSV chứa dấu ngoặc kép chưa đóng");
        }
        values.add(current.toString().trim());
        return values;
    }

    private BigDecimal parseRevenue(String rawRevenue, int row, List<String> warnings) {
        String normalized = rawRevenue.trim().replace(" ", "").replace(",", "").replace("₫", "");
        if (normalized.isBlank()) {
            addWarning(warnings, "Dòng " + row + " không có doanh thu.");
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal revenue = new BigDecimal(normalized);
            if (revenue.signum() < 0) {
                addWarning(warnings, "Dòng " + row + " có doanh thu âm và không được cộng vào tổng.");
                return BigDecimal.ZERO;
            }
            return revenue;
        } catch (NumberFormatException ex) {
            addWarning(warnings, "Dòng " + row + " có doanh thu không hợp lệ và không được cộng vào tổng.");
            return BigDecimal.ZERO;
        }
    }

    private void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(warning);
        } else if (warnings.size() == MAX_WARNINGS) {
            warnings.add("Các cảnh báo còn lại đã được lược bỏ; hãy sửa file và xem trước lại.");
        }
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private AppException invalidFile(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }
}
