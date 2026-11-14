package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import vn.unicom.thi360.shared.api.ValidationException;

class ImportWorkbookCodecTests {

    @Test
    void parseUploadedWorkbook_rejectsValidOoxmlBeyondConfiguredEntryLimit_thatParsesWhenLimitsDisabledOrPoiDefault()
            throws Exception {
        byte[] validBase = buildValidSingleRowXlsx("zip.guard.user", "Valid@1234", "Zip Guard User");
        byte[] validOoxmlBomb = buildValidOoxmlSharedStringsBomb(validBase);

        double prevRatio = ZipSecureFile.getMinInflateRatio();
        long prevMaxEntry = ZipSecureFile.getMaxEntrySize();
        long prevMaxText = ZipSecureFile.getMaxTextSize();
        try {
            // 1. With ZipSecureFile limits completely disabled, the exact fixture parses as valid 1-row OOXML
            ZipSecureFile.setMinInflateRatio(0.0d);
            ZipSecureFile.setMaxEntrySize(Long.MAX_VALUE);
            ZipSecureFile.setMaxTextSize(Long.MAX_VALUE);
            assertParsesSingleUser(validOoxmlBomb, "zip.guard.user");

            // 2. Even under POI defaults (0.01 ratio, 4 GiB entry, 10 MiB text), the fixture still parses
            ZipSecureFile.setMinInflateRatio(0.01d);
            ZipSecureFile.setMaxEntrySize(0xFFFFFFFFL);
            ZipSecureFile.setMaxTextSize(10L * 1024L * 1024L);
            assertParsesSingleUser(validOoxmlBomb, "zip.guard.user");

            // 3. With ImportWorkbookCodec.configureZipSecureLimits() active, upload parsing MUST reject it
            ImportWorkbookCodec codec = new ImportWorkbookCodec(new ImportExcelSchemaHelper(new StaticMessageSource()));
            assertThatThrownBy(() -> codec.parseUploadedWorkbook(validOoxmlBomb, ImportExcelSchemaHelper.DEFAULT_LOCALE))
                    .isInstanceOf(ValidationException.class)
                    .extracting(ex -> ((ValidationException) ex).getCode())
                    .isEqualTo("IMPORT_INVALID_FILE");
        } finally {
            ZipSecureFile.setMinInflateRatio(prevRatio);
            ZipSecureFile.setMaxEntrySize(prevMaxEntry);
            ZipSecureFile.setMaxTextSize(prevMaxText);
        }
    }

    private static void assertParsesSingleUser(byte[] xlsxBytes, String expectedUsername) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(xlsxBytes));
                XSSFWorkbook wb = new XSSFWorkbook(pkg)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(fmt.formatCellValue(sheet.getRow(1).getCell(0))).isEqualTo(expectedUsername);
        }
    }

    private static byte[] buildValidSingleRowXlsx(String username, String password, String fullName) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("DanhSachTaiKhoan");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên đăng nhập*");
            header.createCell(1).setCellValue("Mật khẩu");
            header.createCell(2).setCellValue("Họ và tên*");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(username);
            row.createCell(1).setCellValue(password);
            row.createCell(2).setCellValue(fullName);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildValidOoxmlSharedStringsBomb(byte[] baseXlsx) throws IOException {
        // 250 KiB random alphanumeric (~190 KiB compressed) + 11.3 MiB 'A' (~15 KiB compressed)
        // inside a valid XML comment: total zip < 5 MB, entry size > 10 MiB (ZIP_MAX_ENTRY_SIZE),
        // and inflate ratio ~0.018 > 0.01 (passes POI default ratio and disabled limits).
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random rng = new Random(42L);
        StringBuilder commentPayload = new StringBuilder(11 * 1024 * 1024 + 512 * 1024);
        commentPayload.append("<!-- ");
        for (int i = 0; i < 250 * 1024; i++) {
            commentPayload.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        commentPayload.append("A".repeat(11 * 1024 * 1024 + 300 * 1024));
        commentPayload.append(" -->");

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(baseXlsx));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new ZipEntry(entry.getName()));
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    String originalXml = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    int closeIdx = originalXml.lastIndexOf("</sst>");
                    assertThat(closeIdx).isPositive();
                    String expandedXml = originalXml.substring(0, closeIdx)
                            + commentPayload
                            + originalXml.substring(closeIdx);
                    zout.write(expandedXml.getBytes(StandardCharsets.UTF_8));
                } else {
                    zin.transferTo(zout);
                }
                zout.closeEntry();
            }
            zout.finish();
            return out.toByteArray();
        }
    }
}
