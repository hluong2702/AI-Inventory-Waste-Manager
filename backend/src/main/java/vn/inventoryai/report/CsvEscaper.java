package vn.inventoryai.report;

import java.io.IOException;

final class CsvEscaper {
    private CsvEscaper() {
    }

    static void appendRow(Appendable csv, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(cell(values[i]));
        }
        csv.append("\r\n");
    }

    static String cell(Object raw) {
        String value = raw == null ? "" : raw.toString();
        if (!value.isEmpty()) {
            String withoutLeadingWhitespace = value.stripLeading();
            boolean formulaPrefix = !withoutLeadingWhitespace.isEmpty()
                    && "=+-@".indexOf(withoutLeadingWhitespace.charAt(0)) >= 0;
            if (formulaPrefix || value.charAt(0) == '\t' || value.charAt(0) == '\r') {
                value = "'" + value;
            }
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
