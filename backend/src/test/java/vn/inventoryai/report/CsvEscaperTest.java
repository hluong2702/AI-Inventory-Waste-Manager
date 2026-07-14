package vn.inventoryai.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvEscaperTest {
    @Test
    void escapesCsvSpecialCharacters() {
        assertThat(CsvEscaper.cell("Cửa hàng, Quận 1")).isEqualTo("\"Cửa hàng, Quận 1\"");
        assertThat(CsvEscaper.cell("Nguyên liệu \"A\"")).isEqualTo("\"Nguyên liệu \"\"A\"\"\"");
        assertThat(CsvEscaper.cell("hai\ndòng")).isEqualTo("\"hai\ndòng\"");
        assertThat(CsvEscaper.cell("Bình thường")).isEqualTo("Bình thường");
    }

    @Test
    void neutralizesSpreadsheetFormulaPrefixes() {
        assertThat(CsvEscaper.cell("=SUM(A1:A2)")).isEqualTo("'=SUM(A1:A2)");
        assertThat(CsvEscaper.cell("+1")).isEqualTo("'+1");
        assertThat(CsvEscaper.cell("-1")).isEqualTo("'-1");
        assertThat(CsvEscaper.cell("@cmd")).isEqualTo("'@cmd");
    }
}
