package vn.unicom.thi360.identity.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.api.PermissionSourceResponse;
import vn.unicom.thi360.identity.api.PermissionSourceType;
import vn.unicom.thi360.identity.api.PermissionUserResponse;
import vn.unicom.thi360.identity.domain.UserStatus;

@Component
public class PermissionUsersExportWriter {

    private static final String[] HEADERS = {
        "Tên đăng nhập", "Họ tên", "Email", "Trạng thái", "Nguồn quyền"
    };

    public byte[] write(String permissionCode, List<PermissionUserResponse> rows) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < rows.size(); i++) {
                PermissionUserResponse user = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(safe(user.username()));
                row.createCell(1).setCellValue(safe(user.fullName()));
                row.createCell(2).setCellValue(safe(user.email()));
                row.createCell(3).setCellValue(formatStatus(user.status()));
                row.createCell(4).setCellValue(formatSources(user.sources()));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, i == 4 ? 42 * 256 : 24 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export permission users workbook for " + permissionCode, ex);
        }
    }

    private static String formatStatus(UserStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PROVISIONING -> "Chờ kích hoạt";
            case ACTIVE -> "Hoạt động";
            case INACTIVE -> "Ngừng hoạt động";
        };
    }

    private static String formatSources(List<PermissionSourceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        return sources.stream()
                .map(src -> {
                    if (src.type() == PermissionSourceType.ROLE) {
                        return "Vai trò: " + safe(src.roleName());
                    }
                    String by = safe(src.grantedByUsername());
                    return by.isEmpty() ? "Gán trực tiếp" : "Gán trực tiếp (" + by + ")";
                })
                .collect(Collectors.joining("; "));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
