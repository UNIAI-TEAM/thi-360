package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.PermissionQueryService;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Permissions", description = "Tra cứu danh mục quyền cố định và người dùng theo quyền")
@RestController
@RequestMapping("/api/v1/admin/permissions")
public class AdminPermissionController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PermissionQueryService permissionQueryService;

    public AdminPermissionController(PermissionQueryService permissionQueryService) {
        this.permissionQueryService = permissionQueryService;
    }

    @GetMapping
    @RequirePermission(Permissions.PERMISSION_VIEW)
    @Operation(summary = "Danh sách quyền cố định", description = "Chỉ đọc; lọc theo từ khóa, nhóm đối tượng và phạm vi")
    public List<PermissionResponse> listPermissions(
            @Parameter(description = "Từ khóa tìm kiếm theo mã, tên, mô tả")
            @RequestParam(required = false) String q,
            @Parameter(description = "Mã đối tượng nhóm quyền (vd: USER, ROLE)")
            @RequestParam(required = false) String group,
            @Parameter(description = "Phạm vi quyền")
            @RequestParam(required = false) Access scope) {
        return permissionQueryService.listPermissions(q, group, scope);
    }

    @GetMapping("/{id}/users")
    @RequirePermission({Permissions.PERMISSION_VIEW, Permissions.USER_VIEW})
    @Operation(summary = "Danh sách tài khoản đang sở hữu quyền", description = "Trả về các tài khoản kèm nguồn quyền (vai trò hoặc gán trực tiếp)")
    public PageResponse<PermissionUserResponse> listUsersByPermission(
            @PathVariable UUID id,
            @RequestParam(required = false) PermissionSourceType source,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page,
                size,
                sort,
                PermissionQueryService.ALLOWED_USER_SORT_FIELDS,
                Sort.by("username").ascending());
        return permissionQueryService.findUsersByPermission(id, source, status, q, pageQuery);
    }

    @GetMapping("/{id}/users/export")
    @RequirePermission({Permissions.PERMISSION_VIEW, Permissions.USER_VIEW})
    @Operation(summary = "Xuất Excel tài khoản theo quyền", description = "Xuất file .xlsx theo đúng bộ lọc đang xem")
    public ResponseEntity<byte[]> exportUsersByPermission(
            @PathVariable UUID id,
            @RequestParam(required = false) PermissionSourceType source,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                0,
                1,
                sort,
                PermissionQueryService.ALLOWED_USER_SORT_FIELDS,
                Sort.by("username").ascending());
        PermissionQueryService.PermissionExportResult result =
                permissionQueryService.exportUsersByPermission(id, source, status, q, pageQuery.sort());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment().filename(result.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(result.content());
    }
}
