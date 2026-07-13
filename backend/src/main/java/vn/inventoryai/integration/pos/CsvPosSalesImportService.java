package vn.inventoryai.integration.pos;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvPosSalesImportService implements PosSalesImportService {
    @Override
    public String provider() {
        return "CSV";
    }

    @Override
    public PosSalesImportResult importSales(MultipartFile file) {
        int imported = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return new PosSalesImportResult(provider(), 0, BigDecimal.ZERO, List.of("File rỗng"));
            }
            String[] headers = header.toLowerCase().split(",");
            int revenueIndex = indexOf(headers, "revenue", "doanhthu", "total", "tongtien");
            String line;
            int row = 1;
            while ((line = reader.readLine()) != null) {
                row++;
                if (line.isBlank()) continue;
                String[] values = line.split(",");
                if (revenueIndex >= 0 && revenueIndex < values.length) {
                    totalRevenue = totalRevenue.add(new BigDecimal(values[revenueIndex].trim().replace(",", "")));
                } else if (revenueIndex < 0) {
                    warnings.add("Không tìm thấy cột doanh thu; dòng bán vẫn được ghi nhận tổng số lượng.");
                }
                imported++;
            }
        } catch (Exception ex) {
            warnings.add("Không đọc được file sales CSV: " + ex.getMessage());
        }
        return new PosSalesImportResult(provider(), imported, totalRevenue, warnings);
    }

    private int indexOf(String[] headers, String... names) {
        for (int i = 0; i < headers.length; i++) {
            String normalized = headers[i].trim().replace("_", "").replace(" ", "");
            for (String name : names) {
                if (normalized.equals(name)) return i;
            }
        }
        return -1;
    }
}
