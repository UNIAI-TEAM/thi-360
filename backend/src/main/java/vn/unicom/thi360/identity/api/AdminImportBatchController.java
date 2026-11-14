package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.identity.application.ImportBatchService;
import vn.unicom.thi360.identity.application.ImportExcelSchemaHelper;
import vn.unicom.thi360.identity.domain.ImportRowStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Import Batches", description = "Cấp tài khoản học viên hàng loạt từ tệp Excel")
@RestController
@RequestMapping("/api/v1/admin/import-batches")
public class AdminImportBatchController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportBatchService importBatchService;

    public AdminImportBatchController(ImportBatchService importBatchService) {
        this.importBatchService = importBatchService;
    }

    @GetMapping("/template")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Tải tệp Excel mẫu cấp tài khoản hàng loạt")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] bytes = importBatchService.buildTemplate(ImportExcelSchemaHelper.DEFAULT_LOCALE);
        return xlsxAttachment("mau-cap-tai-khoan-hang-loat.xlsx", bytes);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Tải lên tệp Excel để kiểm tra dữ liệu trước khi cấp tài khoản hàng loạt")
    public ImportBatchResponse uploadBatch(
            @Parameter(description = "Tệp Excel (.xlsx, tối đa 5 MB, tối đa 2.000 dòng dữ liệu)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Bắt buộc đổi mật khẩu ở lần đăng nhập đầu (mặc định false)")
            @RequestParam(value = "mustChangePassword", required = false, defaultValue = "false")
            boolean mustChangePassword) {
        return importBatchService.uploadBatch(file, mustChangePassword, ImportExcelSchemaHelper.DEFAULT_LOCALE);
    }

    @GetMapping("/{id}")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Xem trạng thái và thống kê của đợt cấp tài khoản hàng loạt")
    public ImportBatchResponse getBatch(@PathVariable UUID id) {
        return importBatchService.getBatch(id);
    }

    @GetMapping("/{id}/rows")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Danh sách dòng dữ liệu và lỗi của đợt cấp tài khoản")
    public PageResponse<ImportRowResponse> listBatchRows(
            @PathVariable UUID id,
            @Parameter(description = "Lọc theo trạng thái dòng")
            @RequestParam(required = false) ImportRowStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page,
                size,
                sort,
                ImportBatchService.ALLOWED_ROW_SORT_FIELDS,
                Sort.by(Sort.Direction.ASC, "rowNo"));
        return importBatchService.listBatchRows(id, status, pageQuery);
    }

    @GetMapping("/{id}/errors")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Tải tệp Excel chỉ gồm các dòng lỗi kèm cột Lỗi")
    public ResponseEntity<byte[]> downloadErrors(@PathVariable UUID id) {
        byte[] bytes = importBatchService.downloadErrorsWorkbook(id, ImportExcelSchemaHelper.DEFAULT_LOCALE);
        return xlsxAttachment("import-errors-" + id + ".xlsx", bytes);
    }

    @PostMapping("/{id}/confirm")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Xác nhận chạy cấp tài khoản hàng loạt (hỗ trợ Idempotency-Key)")
    public ImportBatchResponse confirmBatch(
            @PathVariable UUID id,
            @Parameter(description = "Khóa chống trùng lặp xác nhận", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return importBatchService.confirmBatch(id, idempotencyKey, ImportExcelSchemaHelper.DEFAULT_LOCALE);
    }

    @GetMapping("/{id}/result")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Tải tệp Excel kết quả cấp tài khoản (chỉ chứa mật khẩu của dòng do hệ thống sinh)")
    public ResponseEntity<byte[]> downloadResult(@PathVariable UUID id) {
        byte[] bytes = importBatchService.downloadResultWorkbook(id);
        return xlsxAttachment("import-result-" + id + ".xlsx", bytes);
    }

    @DeleteMapping("/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Xóa tệp kết quả của đợt cấp tài khoản")
    public void deleteResult(@PathVariable UUID id) {
        importBatchService.deleteResultWorkbook(id);
    }

    private static ResponseEntity<byte[]> xlsxAttachment(String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
