package vn.inventoryai.integration.pos;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.inventoryai.common.error.AppException;
import vn.inventoryai.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvPosSalesImportServiceTest {
    private final CsvPosSalesImportService service = new CsvPosSalesImportService();

    @Test
    void previewsQuotedCsvWithoutClaimingPersistence() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "order,revenue\nA-1,\"1,200\"\nA-2,300\n".getBytes(StandardCharsets.UTF_8)
        );

        PosSalesImportResult result = service.previewSales(file);

        assertThat(result.mode()).isEqualTo("PREVIEW_ONLY");
        assertThat(result.persisted()).isFalse();
        assertThat(result.rowsParsed()).isEqualTo(2);
        assertThat(result.totalRevenue()).isEqualByComparingTo("1500");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rejectsEmptyAndMalformedCsv() {
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);
        MockMultipartFile malformed = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "order,revenue\nA-1,\"1200\n".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.previewSales(empty))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.previewSales(malformed))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
