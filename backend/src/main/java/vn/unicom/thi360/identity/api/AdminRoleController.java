package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.RoleService;
import vn.unicom.thi360.identity.domain.RoleStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Roles", description = "Quản lý vai trò và tập quyền của vai trò")
@RestController
@RequestMapping("/api/v1/admin/roles")
public class AdminRoleController {

    private final RoleService roleService;

    public AdminRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission(Permissions.ROLE_VIEW)
    @Operation(summary = "Danh sách vai trò")
    public PageResponse<RoleSummaryResponse> listRoles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Access access,
            @RequestParam(required = false) RoleStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page, size, sort, RoleService.ALLOWED_SORT_FIELDS, Sort.unsorted());
        return roleService.listRoles(q, access, status, pageQuery);
    }

    @GetMapping("/{id}")
    @RequirePermission(Permissions.ROLE_VIEW)
    @Operation(summary = "Chi tiết vai trò")
    public RoleDetailResponse getRole(@PathVariable UUID id) {
        return roleService.getRole(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.ROLE_MODIFY)
    @Operation(summary = "Tạo vai trò mới")
    public RoleDetailResponse createRole(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.createRole(request);
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permissions.ROLE_MODIFY)
    @Operation(summary = "Cập nhật thông tin vai trò")
    public RoleDetailResponse updateRole(
            @PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.updateRole(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.ROLE_MODIFY)
    @Operation(summary = "Xóa vai trò")
    public void deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(Permissions.ROLE_MODIFY)
    @Operation(summary = "Cập nhật toàn bộ quyền của vai trò")
    public RoleDetailResponse updateRolePermissions(
            @PathVariable UUID id, @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return roleService.updateRolePermissions(id, request);
    }
}
