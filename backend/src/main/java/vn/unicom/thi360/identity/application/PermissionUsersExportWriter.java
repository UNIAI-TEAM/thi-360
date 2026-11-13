package vn.unicom.thi360.identity.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.api.PermissionSourceResponse;
import vn.unicom.thi360.identity.api.PermissionSourceType;
import vn.unicom.thi360.identity.api.PermissionUserResponse;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.shared.web.AppMessages;

@Component
public class PermissionUsersExportWriter {

    private static final String[] HEADER_KEYS = {
        "export.permissionUsers.header.username",
        "export.permissionUsers.header.fullName",
        "export.permissionUsers.header.email",
        "export.permissionUsers.header.status",
        "export.permissionUsers.header.sources"
    };

    private final AppMessages messages;

    public PermissionUsersExportWriter() {
        this(AppMessages.defaultInstance());
    }

    @Autowired
    public PermissionUsersExportWriter(AppMessages messages) {
        this.messages = messages != null ? messages : AppMessages.defaultInstance();
    }

    public byte[] write(String permissionCode, List<PermissionUserResponse> rows) {
        return write(permissionCode, rows, messages.currentLocale());
    }

    public byte[] write(String permissionCode, List<PermissionUserResponse> rows, Locale locale) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < HEADER_KEYS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(messages.get(locale, HEADER_KEYS[i]));
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < rows.size(); i++) {
                PermissionUserResponse user = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(safe(user.username()));
                row.createCell(1).setCellValue(safe(user.fullName()));
                row.createCell(2).setCellValue(safe(user.email()));
                row.createCell(3).setCellValue(formatStatus(user.status(), locale));
                row.createCell(4).setCellValue(formatSources(user.sources(), locale));
            }

            for (int i = 0; i < HEADER_KEYS.length; i++) {
                sheet.setColumnWidth(i, i == 4 ? 42 * 256 : 24 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export permission users workbook for " + permissionCode, ex);
        }
    }

    private String formatStatus(UserStatus status, Locale locale) {
        if (status == null) {
            return "";
        }
        return messages.get(locale, "export.permissionUsers.status." + status.name());
    }

    private String formatSources(List<PermissionSourceResponse> sources, Locale locale) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        return sources.stream()
                .map(src -> {
                    if (src.type() == PermissionSourceType.ROLE) {
                        return messages.get(locale, "export.permissionUsers.source.role", safe(src.roleName()));
                    }
                    String by = safe(src.grantedByUsername());
                    return by.isEmpty()
                            ? messages.get(locale, "export.permissionUsers.source.direct")
                            : messages.get(locale, "export.permissionUsers.source.directBy", by);
                })
                .collect(Collectors.joining("; "));
    }
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
