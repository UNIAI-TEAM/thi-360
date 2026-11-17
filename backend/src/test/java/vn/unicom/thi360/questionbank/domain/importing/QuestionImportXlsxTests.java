package vn.unicom.thi360.questionbank.domain.importing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class QuestionImportXlsxTests {
    private final XlsxImportSheetReader reader = new XlsxImportSheetReader();

    @Test
    void macroEntryRejected() throws Exception {
        byte[] file = rewrite(QuestionImportTemplate.create("Questions"), false, true);
        assertEquals(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, ImportLimits.DEFAULT)).code());
        assertEquals(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsm", file, ImportLimits.DEFAULT)).code());
    }

    @Test
    void zipBombRejected() throws Exception {
        byte[] file = rewrite(QuestionImportTemplate.create("Questions"), true, false);
        assertEquals(ImportIssueCode.IMPORT_INVALID_FILE,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, ImportLimits.DEFAULT)).code());
    }

    @Test
    void workbookRespectsLimits() {
        byte[] file = QuestionImportTemplate.create("Questions");
        assertEquals(ImportIssueCode.IMPORT_FILE_TOO_LARGE,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, new ImportLimits(5, 2000))).code());
        assertEquals(ImportIssueCode.IMPORT_ROW_LIMIT_EXCEEDED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, new ImportLimits(5_000_000, 1))).code());
    }

    @Test
    void sparseSheetStaysBounded() throws Exception {
        // Tệp thưa: ô tiêu đề ở cột xa (XFD) bị chặn bởi giới hạn cột; ô dữ liệu ở dòng
        // 1_048_575 không bung thành ma trận — chỉ các dòng vật lý được đọc, giữ số dòng gốc.
        byte[] wide = sparseXlsx(16_383, false, 0);
        assertEquals(ImportIssueCode.IMPORT_INVALID_HEADER,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", wide, ImportLimits.DEFAULT)).code());

        byte[] deep = sparseXlsx(0, true, 1_048_575);
        ImportSheet sheet = reader.read("a.xlsx", deep, ImportLimits.DEFAULT);
        assertEquals(1, sheet.rows().size());
        assertEquals(1_048_576, sheet.rows().getFirst().rowNumber());
        assertEquals("far", sheet.rows().getFirst().cell("c0"));
    }

    private static byte[] sparseXlsx(int headerCol, boolean farDataRow, int dataRowIndex) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("S");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("type");
            if (headerCol > 0) {
                header.createCell(headerCol).setCellValue("far-header");
            }
            if (farDataRow) {
                sheet.createRow(dataRowIndex).createCell(0).setCellValue("far");
            } else {
                sheet.createRow(1).createCell(0).setCellValue("x");
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] rewrite(byte[] input, boolean bomb, boolean macro) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(input));
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (bomb && entry.getName().equals("xl/sharedStrings.xml")) {
                    out.write(new byte[11 * 1024 * 1024]);
                } else {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
            if (macro) {
                out.putNextEntry(new ZipEntry("xl/vbaProject.bin"));
                out.write(1);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
