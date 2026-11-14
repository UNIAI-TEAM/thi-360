package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.BulkAuthzService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Bulk Authz", description = "Gán hoặc gỡ vai trò, quyền trực tiếp hàng loạt")
@RestController
@RequestMapping("/api/v1/admin/authz/bulk")
public class AdminBulkAuthzController {

    private final BulkAuthzService bulkAuthzService;

    public AdminBulkAuthzController(BulkAuthzService bulkAuthzService) {
        this.bulkAuthzService = bulkAuthzService;
    }

    @PostMapping("/preview")
    @RequirePermission(Permissions.USER_ASSIGN)
    @Operation(summary = "Xem trước kết quả gán/gỡ quyền hàng loạt")
    public BulkAuthzResponse preview(@Valid @RequestBody BulkAuthzRequest request) {
        return bulkAuthzService.preview(request);
    }

    @PostMapping
    @RequirePermission(Permissions.USER_ASSIGN)
    @Operation(summary = "Áp dụng gán/gỡ quyền hàng loạt (idempotent)")
    public BulkAuthzResponse apply(
            @Parameter(description = "Khóa chống trùng lặp thao tác", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BulkAuthzRequest request) {
        return bulkAuthzService.apply(request, idempotencyKey);
    }
}
