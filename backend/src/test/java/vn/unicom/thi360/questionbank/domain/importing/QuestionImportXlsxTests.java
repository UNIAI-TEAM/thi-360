package vn.unicom.thi360.questionbank.domain.importing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class QuestionImportXlsxTests {
    private final XlsxImportSheetReader reader = new XlsxImportSheetReader();

    @Test
    void macroEntryRejected() throws Exception {
        byte[] file = rewrite(QuestionImportTemplate.create(), false, true);
        assertEquals(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, ImportLimits.DEFAULT)).code());
        assertEquals(ImportIssueCode.IMPORT_MACRO_NOT_ALLOWED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsm", file, ImportLimits.DEFAULT)).code());
    }

    @Test
    void zipBombRejected() throws Exception {
        byte[] file = rewrite(QuestionImportTemplate.create(), true, false);
        assertEquals(ImportIssueCode.IMPORT_INVALID_FILE,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, ImportLimits.DEFAULT)).code());
    }

    @Test
    void workbookRespectsLimits() {
        byte[] file = QuestionImportTemplate.create();
        assertEquals(ImportIssueCode.IMPORT_FILE_TOO_LARGE,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, new ImportLimits(5, 2000))).code());
        assertEquals(ImportIssueCode.IMPORT_ROW_LIMIT_EXCEEDED,
            assertThrows(ImportFileException.class, () -> reader.read("a.xlsx", file, new ImportLimits(5_000_000, 1))).code());
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
