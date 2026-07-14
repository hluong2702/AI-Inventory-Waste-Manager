package vn.inventoryai.report;

final class CsvEscaper {
    private CsvEscaper() {
    }

    static void appendRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(cell(values[i]));
        }
        csv.append("\r\n");
    }

    static String cell(Object raw) {
        String value = raw == null ? "" : raw.toString();
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
