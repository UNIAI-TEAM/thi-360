package vn.unicom.thi360.questionbank.domain.importing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class QuestionImportCsvTests {
    private final CsvImportSheetReader reader = new CsvImportSheetReader();

    @Test
    void csvBomQuotingAndSemicolon() throws Exception {
        ImportSheet comma = reader.read("a.csv", "\ufeffa,b\r\n\"x, y\",\"say \"\"hi\"\"\"\n".getBytes(StandardCharsets.UTF_8), ImportLimits.DEFAULT);
        assertEquals("x, y", comma.rows().getFirst().cell("c0"));
        assertEquals("say \"hi\"", comma.rows().getFirst().cell("c1"));
        ImportSheet semicolon = reader.read("a.csv", "a;b\n1;\"2\n3\"".getBytes(StandardCharsets.UTF_8), ImportLimits.DEFAULT);
        assertEquals("2\n3", semicolon.rows().getFirst().cell("c1"));
    }

    @Test
    void tableSkipsEmptyRowsAndKeepsNumbers() throws Exception {
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null,
            List.of(List.of("A", "B"), List.of("1", "2"), List.of("", ""), List.of("3", "")), ImportLimits.DEFAULT);
        assertEquals(List.of(2, 4), sheet.rows().stream().map(ImportSheetRow::rowNumber).toList());
    }

    @Test
    void malformedQuotingIsInvalidFileNot500() throws Exception {
        // Iterator của Commons CSV ném UncheckedIOException khi ô ngoặc kép không đóng
        // (R1-2): vẫn phải là IMPORT_INVALID_FILE (422 tại endpoint upload).
        assertEquals(ImportIssueCode.IMPORT_INVALID_FILE,
            assertThrows(ImportFileException.class, () -> reader.read("a.csv",
                "type,content\nSHORT_ANSWER,\"unterminated".getBytes(StandardCharsets.UTF_8),
                ImportLimits.DEFAULT)).code());
    }

    @Test
    void fileAndRowLimitsAndMacros() {
        assertEquals(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsm", new byte[] {1}, ImportLimits.DEFAULT)).code());
        assertEquals(ImportIssueCode.IMPORT_FILE_TOO_LARGE,
            assertThrows(ImportFileException.class, () -> reader.read("a.csv", new byte[] {1, 2}, new ImportLimits(1, 2))).code());
        assertEquals(ImportIssueCode.IMPORT_ROW_LIMIT_EXCEEDED,
            assertThrows(ImportFileException.class, () -> reader.read("a.csv", "a\n1\n2".getBytes(StandardCharsets.UTF_8), new ImportLimits(100, 1))).code());
    }
}
