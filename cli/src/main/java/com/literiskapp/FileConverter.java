package com.literiskapp;

import com.fasterxml.jackson.core.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * File conversion utilities: JSON ↔ CSV, JSON ↔ XLSX.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Two-pass approach: first pass discovers the full column set and infers
 *       data types; second pass performs the actual conversion.</li>
 *   <li>Data is streamed row-by-row where possible (Excel is held in memory
 *       as required by Apache POI).</li>
 *   <li>JSON input files may contain non-JSON preamble lines (e.g. "HTTP 200")
 *       which are skipped automatically. The actual JSON must be an array of
 *       flat objects: {@code [ { "field": value, … }, … ]}.</li>
 *   <li>When an object is missing a field it is written as empty in tabular
 *       output; empty tabular cells are omitted when writing JSON.</li>
 * </ul>
 */
public class FileConverter {

    // ── Inferred column type (used for both JSON and CSV sources) ──────────
    private enum ColType { UNKNOWN, INTEGER, DECIMAL, BOOLEAN, STRING }

    // ════════════════════════════════════════════════════════════════════════
    // JSON → CSV
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Reads a JSON array-of-objects file and writes a CSV file next to it.
     * Output file name: same stem, extension replaced with {@code .csv}.
     */
    public static void jsonToCsv(Path input) throws Exception {
        int skip = countPreamble(input);

        // ── Pass 1: discover ordered column set ──
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (JsonParser p = openParser(input, skip)) {
            advanceToArray(p);
            while (p.nextToken() == JsonToken.START_OBJECT) {
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    seen.add(p.currentName());
                    p.nextToken(); // skip value
                }
            }
        }
        columns.addAll(seen);

        Path output = replaceExtension(input, "csv");
        System.out.println("Output → " + output);

        // ── Pass 2: stream rows and write CSV ──
        try (BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             JsonParser p = openParser(input, skip)) {
            // header
            w.write(joinCsv(columns.stream().map(FileConverter::csvField).toList()));
            advanceToArray(p);
            while (p.nextToken() == JsonToken.START_OBJECT) {
                Map<String, String> row = readJsonRow(p);
                w.newLine();
                w.write(joinCsv(columns.stream()
                        .map(c -> csvField(row.getOrDefault(c, "")))
                        .toList()));
            }
        }
        System.out.println("Done — " + columns.size() + " columns.");
    }

    // ════════════════════════════════════════════════════════════════════════
    // CSV → JSON
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Reads a CSV file and writes a JSON array-of-objects file next to it.
     * Output file name: same stem, extension replaced with {@code .json}.
     * Column types are inferred from a full scan before conversion.
     */
    public static void csvToJson(Path input) throws Exception {
        List<String> headers;
        ColType[] types;

        // ── Pass 1: read header + scan all values for type inference ──
        try (BufferedReader br = openReader(input)) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IllegalArgumentException("CSV file is empty");
            headers = parseCsvLine(headerLine);
            types = new ColType[headers.size()];
            Arrays.fill(types, ColType.UNKNOWN);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = parseCsvLine(line);
                for (int i = 0; i < Math.min(cells.size(), types.length); i++) {
                    String val = cells.get(i);
                    if (!val.isEmpty()) types[i] = promoteFromString(types[i], val);
                }
            }
        }
        // Columns that were entirely empty/null → treat as STRING
        for (int i = 0; i < types.length; i++) {
            if (types[i] == ColType.UNKNOWN) types[i] = ColType.STRING;
        }

        Path output = replaceExtension(input, "json");
        System.out.println("Output → " + output);

        // ── Pass 2: stream rows and write JSON ──
        try (BufferedReader br = openReader(input);
             JsonGenerator gen = new JsonFactory()
                     .createGenerator(output.toFile(), JsonEncoding.UTF8)) {
            gen.useDefaultPrettyPrinter();
            br.readLine(); // skip header line
            gen.writeStartArray();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = parseCsvLine(line);
                gen.writeStartObject();
                for (int i = 0; i < headers.size(); i++) {
                    String val = i < cells.size() ? cells.get(i) : "";
                    if (val.isEmpty()) continue; // omit empty fields in JSON
                    gen.writeFieldName(headers.get(i));
                    writeTypedValue(gen, val, types[i]);
                }
                gen.writeEndObject();
            }
            gen.writeEndArray();
        }
        System.out.println("Done — " + headers.size() + " columns.");
    }

    // ════════════════════════════════════════════════════════════════════════
    // JSON → XLSX
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Reads a JSON array-of-objects file and writes an XLSX file next to it.
     * Output file name: same stem, extension replaced with {@code .xlsx}.
     * Cell types (STRING, NUMERIC, BOOLEAN) are inferred from a full scan.
     */
    public static void jsonToXls(Path input) throws Exception {
        int skip = countPreamble(input);

        // ── Pass 1: discover columns and infer types ──
        List<String> columns = new ArrayList<>();
        Map<String, ColType> typeMap = new LinkedHashMap<>();
        try (JsonParser p = openParser(input, skip)) {
            advanceToArray(p);
            while (p.nextToken() == JsonToken.START_OBJECT) {
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String name = p.currentName();
                    JsonToken valTok = p.nextToken();
                    if (!typeMap.containsKey(name)) {
                        columns.add(name);
                        typeMap.put(name, ColType.UNKNOWN);
                    }
                    typeMap.put(name, promoteFromJsonToken(typeMap.get(name), valTok));
                }
            }
        }
        for (String c : columns) {
            if (typeMap.get(c) == ColType.UNKNOWN) typeMap.put(c, ColType.STRING);
        }

        Path output = replaceExtension(input, "xlsx");
        System.out.println("Output → " + output);

        // ── Pass 2: build workbook row-by-row ──
        // XSSFWorkbook holds all rows in memory — this is the expected POI trade-off.
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Data");

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                headerRow.createCell(i, CellType.STRING).setCellValue(columns.get(i));
            }

            // Data rows
            int rowNum = 1;
            try (JsonParser p = openParser(input, skip)) {
                advanceToArray(p);
                while (p.nextToken() == JsonToken.START_OBJECT) {
                    Map<String, String> raw = readJsonRow(p);
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 0; i < columns.size(); i++) {
                        String col = columns.get(i);
                        String val = raw.get(col);
                        if (val == null || val.isEmpty()) continue;
                        Cell cell = row.createCell(i);
                        switch (typeMap.get(col)) {
                            case INTEGER, DECIMAL -> {
                                cell.setCellType(CellType.NUMERIC);
                                cell.setCellValue(Double.parseDouble(val));
                            }
                            case BOOLEAN -> {
                                cell.setCellType(CellType.BOOLEAN);
                                cell.setCellValue(Boolean.parseBoolean(val));
                            }
                            default -> {
                                cell.setCellType(CellType.STRING);
                                cell.setCellValue(val);
                            }
                        }
                    }
                }
            }

            try (OutputStream os = Files.newOutputStream(output)) {
                wb.write(os);
            }
        }
        System.out.println("Done — " + columns.size() + " columns, " +
                (countPreamble(output) + 1) + " rows (approx).");
    }

    // ════════════════════════════════════════════════════════════════════════
    // XLSX → JSON
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Reads the first sheet of an XLSX (or XLS) file and writes a JSON array
     * of objects next to it. Output file name: same stem, extension replaced
     * with {@code .json}. Cell types are preserved (NUMERIC, BOOLEAN, STRING).
     */
    public static void xlsToJson(Path input) throws Exception {
        Path output = replaceExtension(input, "json");
        System.out.println("Output → " + output);

        // WorkbookFactory auto-detects .xls vs .xlsx
        try (Workbook wb = WorkbookFactory.create(input.toFile());
             JsonGenerator gen = new JsonFactory()
                     .createGenerator(output.toFile(), JsonEncoding.UTF8)) {
            gen.useDefaultPrettyPrinter();
            gen.writeStartArray();

            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                gen.writeEndArray();
                System.out.println("Done — sheet is empty.");
                return;
            }

            // Collect header names by cell index (gaps in header → skip column)
            int numCols = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>(numCols);
            for (int c = 0; c < numCols; c++) {
                Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String name = (cell != null) ? cell.getStringCellValue().trim() : "";
                headers.add(name);
            }

            int rowsWritten = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlankRow(row, numCols)) continue;

                gen.writeStartObject();
                for (int c = 0; c < headers.size(); c++) {
                    String fieldName = headers.get(c);
                    if (fieldName.isEmpty()) continue; // no header → skip column

                    Cell cell = (row == null) ? null
                            : row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null || cell.getCellType() == CellType.BLANK) continue;

                    writeCellToJson(gen, fieldName, cell);
                }
                gen.writeEndObject();
                rowsWritten++;
            }

            gen.writeEndArray();
            System.out.println("Done — " + headers.size() + " columns, "
                    + rowsWritten + " rows.");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private helpers — JSON streaming
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Counts how many leading lines to skip before the JSON array begins.
     * Stops at the first line whose trimmed form starts with {@code [}.
     */
    private static int countPreamble(Path path) throws IOException {
        try (BufferedReader br = openReader(path)) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("[")) return count;
                count++;
            }
            return 0; // fallback: nothing to skip
        }
    }

    /** Opens a {@link JsonParser} on the file, skipping {@code skipLines} leading lines. */
    private static JsonParser openParser(Path path, int skipLines) throws IOException {
        return new JsonFactory().createParser(openReader(path, skipLines));
    }

    /**
     * Opens a {@link BufferedReader} with automatic encoding detection and a
     * lenient decoder ({@link CodingErrorAction#REPLACE}) so malformed byte
     * sequences are never fatal.
     *
     * <p>Detection order:
     * <ol>
     *   <li>UTF-8 BOM (EF BB BF) → UTF-8, skip 3 bytes</li>
     *   <li>UTF-16 LE BOM (FF FE) → UTF-16 LE, skip 2 bytes</li>
     *   <li>UTF-16 BE BOM (FE FF) → UTF-16 BE, skip 2 bytes</li>
     *   <li>Sample (up to 8 KB) is valid UTF-8 → UTF-8</li>
     *   <li>Fallback → Windows-1252 (superset of ISO-8859-1, common on Windows)</li>
     * </ol>
     */
    private static BufferedReader openReader(Path path) throws IOException {
        // ── Read a small header for BOM + UTF-8 probe ────────────────────
        byte[] head = new byte[8192];
        int n;
        try (InputStream probe = Files.newInputStream(path)) {
            n = probe.read(head);
        }

        Charset charset;
        int skipBytes = 0;

        if (n >= 3
                && head[0] == (byte) 0xEF
                && head[1] == (byte) 0xBB
                && head[2] == (byte) 0xBF) {
            charset   = StandardCharsets.UTF_8;
            skipBytes = 3;                            // UTF-8 BOM
        } else if (n >= 2
                && head[0] == (byte) 0xFF
                && head[1] == (byte) 0xFE) {
            charset   = StandardCharsets.UTF_16LE;
            skipBytes = 2;                            // UTF-16 LE BOM
        } else if (n >= 2
                && head[0] == (byte) 0xFE
                && head[1] == (byte) 0xFF) {
            charset   = StandardCharsets.UTF_16BE;
            skipBytes = 2;                            // UTF-16 BE BOM
        } else {
            // No BOM — probe whether the sample is valid UTF-8
            try {
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(head, 0, n));
                charset = StandardCharsets.UTF_8;
            } catch (CharacterCodingException e) {
                charset = Charset.forName("windows-1252"); // common Windows fallback
            }
        }

        System.out.println("  [detected encoding: " + charset.displayName() + "]");

        InputStream is = Files.newInputStream(path);
        if (skipBytes > 0) is.skipNBytes(skipBytes);

        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)   // never throw on bad bytes
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        return new BufferedReader(new InputStreamReader(is, decoder));
    }

    /** Delegates to {@link #openReader(Path)} and skips {@code skipLines} lines. */
    private static BufferedReader openReader(Path path, int skipLines) throws IOException {
        BufferedReader br = openReader(path);
        for (int i = 0; i < skipLines; i++) br.readLine();
        return br;
    }

    /** Advances the parser to the START_ARRAY token, ignoring anything before it. */
    private static void advanceToArray(JsonParser p) throws IOException {
        JsonToken t;
        while ((t = p.nextToken()) != null && t != JsonToken.START_ARRAY) { /* skip */ }
    }

    /**
     * Reads a single JSON object (parser must be positioned on START_OBJECT)
     * and returns its fields as a String map. Null values become empty strings.
     */
    private static Map<String, String> readJsonRow(JsonParser p) throws IOException {
        Map<String, String> row = new LinkedHashMap<>();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String name = p.currentName();
            JsonToken tok = p.nextToken();
            row.put(name, tok == JsonToken.VALUE_NULL ? "" : p.getText());
        }
        return row;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private helpers — CSV
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Joins a list of pre-escaped fields with commas.
     */
    private static String joinCsv(List<String> fields) {
        return String.join(",", fields);
    }

    /**
     * Sanitizes a single value for CSV output:
     * <ul>
     *   <li>Newline characters are replaced with a single space.</li>
     *   <li>Values containing commas, double-quotes, or semicolons are wrapped
     *       in double-quotes; embedded double-quotes are doubled.</li>
     * </ul>
     */
    private static String csvField(String value) {
        if (value == null || value.isEmpty()) return "";
        // Collapse newlines
        value = value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        // Quote if necessary
        if (value.contains(",") || value.contains("\"") || value.contains(";")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Parses a single CSV line into a list of field strings.
     * Handles RFC 4180 quoting: fields wrapped in {@code "}, internal
     * double-quotes escaped as {@code ""}.
     */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"'); // escaped double-quote
                        i++;
                    } else {
                        inQuote = false; // closing quote
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString()); // last field
        return fields;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private helpers — type inference
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Promotes a column's inferred type given a new string value observed
     * during the CSV→JSON first pass. Types can only widen or fall back to
     * STRING, never narrow.
     */
    private static ColType promoteFromString(ColType current, String value) {
        return switch (current) {
            case UNKNOWN -> {
                if (isBoolean(value))     yield ColType.BOOLEAN;
                if (isLong(value))        yield ColType.INTEGER;
                if (isDouble(value))      yield ColType.DECIMAL;
                yield ColType.STRING;
            }
            case BOOLEAN -> isBoolean(value) ? ColType.BOOLEAN : ColType.STRING;
            case INTEGER -> {
                if (isLong(value))   yield ColType.INTEGER;
                if (isDouble(value)) yield ColType.DECIMAL;
                yield ColType.STRING;
            }
            case DECIMAL -> isDouble(value) ? ColType.DECIMAL : ColType.STRING;
            default      -> ColType.STRING;
        };
    }

    /**
     * Promotes a column's inferred type given the JSON token of a newly seen
     * value during the JSON→XLS first pass. NULL tokens leave the type
     * unchanged.
     */
    private static ColType promoteFromJsonToken(ColType current, JsonToken token) {
        ColType observed = switch (token) {
            case VALUE_NUMBER_INT          -> ColType.INTEGER;
            case VALUE_NUMBER_FLOAT        -> ColType.DECIMAL;
            case VALUE_TRUE, VALUE_FALSE   -> ColType.BOOLEAN;
            case VALUE_STRING              -> ColType.STRING;
            default                        -> ColType.UNKNOWN; // null → no change
        };
        if (observed == ColType.UNKNOWN) return current;
        return switch (current) {
            case UNKNOWN  -> observed;
            case INTEGER  -> (observed == ColType.INTEGER) ? ColType.INTEGER
                           : (observed == ColType.DECIMAL) ? ColType.DECIMAL
                           : ColType.STRING;
            case DECIMAL  -> (observed == ColType.INTEGER || observed == ColType.DECIMAL)
                           ? ColType.DECIMAL : ColType.STRING;
            case BOOLEAN  -> (observed == ColType.BOOLEAN) ? ColType.BOOLEAN : ColType.STRING;
            default       -> ColType.STRING;
        };
    }

    private static boolean isBoolean(String v) {
        return "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
    }

    private static boolean isLong(String v) {
        try { Long.parseLong(v.trim()); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private static boolean isDouble(String v) {
        try { Double.parseDouble(v.trim()); return true; }
        catch (NumberFormatException e) { return false; }
    }

    /** Writes a typed JSON value based on the inferred column type. */
    private static void writeTypedValue(JsonGenerator gen, String val, ColType type)
            throws IOException {
        switch (type) {
            case INTEGER -> gen.writeNumber(Long.parseLong(val.trim()));
            case DECIMAL -> gen.writeNumber(Double.parseDouble(val.trim()));
            case BOOLEAN -> gen.writeBoolean(Boolean.parseBoolean(val));
            default      -> gen.writeString(val);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private helpers — Excel / POI
    // ════════════════════════════════════════════════════════════════════════

    /** Returns true if every cell in the row is null or blank. */
    private static boolean isBlankRow(Row row, int numCols) {
        if (row == null) return true;
        for (int c = 0; c < numCols; c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    /**
     * Writes one Excel cell as a JSON field. Integer-valued NUMERIC cells are
     * written as JSON integers (no trailing ".0"). DATE-formatted NUMERIC cells
     * are written as ISO-8601 strings. FORMULA cells use their cached result.
     * Blank / error cells are silently skipped.
     */
    private static void writeCellToJson(JsonGenerator gen, String fieldName, Cell cell)
            throws IOException {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();

        switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Write as ISO local-date-time string
                    gen.writeStringField(fieldName,
                            cell.getLocalDateTimeCellValue().toString());
                } else {
                    double d = cell.getNumericCellValue();
                    gen.writeFieldName(fieldName);
                    if (d == Math.floor(d) && !Double.isInfinite(d)
                            && Math.abs(d) < 1e15) {
                        gen.writeNumber((long) d); // integer — no decimals
                    } else {
                        gen.writeNumber(d);
                    }
                }
            }
            case BOOLEAN -> gen.writeBooleanField(fieldName, cell.getBooleanCellValue());
            case STRING  -> {
                String s = cell.getStringCellValue();
                if (!s.isEmpty()) gen.writeStringField(fieldName, s);
            }
            default -> { /* BLANK, ERROR — skip */ }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Utility
    // ════════════════════════════════════════════════════════════════════════

    /** Replaces the file extension of {@code path} with {@code newExt} (no leading dot). */
    static Path replaceExtension(Path path, String newExt) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name;
        return path.resolveSibling(base + "." + newExt);
    }
}
