package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.UserAuthzService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin User Authz", description = "Gán vai trò, quyền trực tiếp và xem quyền hiệu lực của tài khoản")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserAuthzController {

    private final UserAuthzService userAuthzService;

    public AdminUserAuthzController(UserAuthzService userAuthzService) {
        this.userAuthzService = userAuthzService;
    }

    @GetMapping("/{id}/roles")
    @RequirePermission(Permissions.USER_VIEW)
    @Operation(summary = "Danh sách vai trò của tài khoản")
    public UserRolesResponse getUserRoles(@PathVariable UUID id) {
        return userAuthzService.getUserRoles(id);
    }

    @PutMapping("/{id}/roles")
    @RequirePermission(Permissions.USER_ASSIGN)
    @Operation(summary = "Thay thế toàn bộ vai trò của tài khoản")
    public UserRolesResponse updateUserRoles(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRolesRequest request) {
        return userAuthzService.updateUserRoles(id, request);
    }

    @GetMapping("/{id}/permissions")
    @RequirePermission(Permissions.USER_VIEW)
    @Operation(summary = "Danh sách quyền gán trực tiếp của tài khoản")
    public UserDirectPermissionsResponse getUserPermissions(@PathVariable UUID id) {
        return userAuthzService.getUserPermissions(id);
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(Permissions.USER_ASSIGN)
    @Operation(summary = "Thay thế toàn bộ quyền gán trực tiếp của tài khoản")
    public UserDirectPermissionsResponse updateUserPermissions(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserPermissionsRequest request) {
        return userAuthzService.updateUserPermissions(id, request);
    }

    @GetMapping("/{id}/effective-permissions")
    @RequirePermission(Permissions.USER_VIEW)
    @Operation(summary = "Danh sách quyền hiệu lực và nguồn cấp của tài khoản")
    public EffectivePermissionsResponse getEffectivePermissions(@PathVariable UUID id) {
        return userAuthzService.getEffectivePermissions(id);
    }
}
