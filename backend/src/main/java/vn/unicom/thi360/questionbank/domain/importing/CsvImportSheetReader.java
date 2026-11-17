package vn.unicom.thi360.questionbank.domain.importing;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Đọc CSV theo RFC 4180 (ô trong ngoặc kép, ngoặc kép kép, xuống dòng trong ô, CRLF), UTF-8,
 * chịu BOM đầu tệp. Dấu phân cách là phẩy, hoặc chấm phẩy khi dòng đầu có nhiều {@code ;} hơn
 * {@code ,} — Excel bản tiếng Việt lưu CSV bằng dấu chấm phẩy (như {@code files.ts}).
 */
public final class CsvImportSheetReader implements ImportSheetReader {

    private static final char BOM = '\uFEFF';
    @Override
    public ImportSheet read(String fileName, byte[] fileBytes, ImportLimits limits) throws ImportFileException {
        ImportFiles.checkCommon(fileName, fileBytes, limits);
        String text = new String(fileBytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new ImportFileException(ImportIssueCode.IMPORT_EMPTY_FILE, "Tệp CSV trống");
        }
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.RFC4180.builder().setDelimiter(delimiter).get();
        List<List<String>> table = new ArrayList<>();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>((int) record.size());
                for (String value : record) {
                    row.add(value);
                }
                table.add(row);
            }
        } catch (IOException | IllegalArgumentException | java.io.UncheckedIOException ex) {
            // Commons CSV gói lỗi đọc của iterator trong UncheckedIOException (ngoặc kép chưa
            // đóng, hàng lỗi cuối tệp) — vẫn là lỗi tệp 422, không phải 500.
            throw new ImportFileException(
                ImportIssueCode.IMPORT_INVALID_FILE, "Tệp CSV không hợp lệ", ex);
        }
        if (table.isEmpty()) {
            throw new ImportFileException(ImportIssueCode.IMPORT_EMPTY_FILE, "Tệp CSV trống");
        }
        return ImportSheets.fromTable(ImportFileType.CSV, null, table, limits);
    }

    /** Dấu phân cách của dòng đầu: nhiều {@code ;} hơn {@code ,} thì dùng {@code ;} (files.ts). */
    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        int semicolons = 0;
        int commas = 0;
        for (int i = 0; i < firstLine.length(); i++) {
            char c = firstLine.charAt(i);
            if (c == ';') {
                semicolons++;
            } else if (c == ',') {
                commas++;
            }
        }
        return semicolons > commas ? ';' : ',';
    }
}
