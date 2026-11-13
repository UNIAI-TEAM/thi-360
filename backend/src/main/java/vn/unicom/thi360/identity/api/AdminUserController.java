package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.UserAdminService;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Users", description = "Quản lý tài khoản người dùng")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @RequirePermission(Permissions.USER_VIEW)
    @Operation(summary = "Danh sách tài khoản người dùng")
    public PageResponse<UserSummaryResponse> listUsers(
            @Parameter(description = "Tìm kiếm theo mã, tên đăng nhập, họ tên, email, số điện thoại")
            @RequestParam(required = false) String q,
            @Parameter(description = "Lọc theo nguồn tạo tài khoản")
            @RequestParam(required = false) UserSource source,
            @Parameter(description = "Lọc theo trạng thái tài khoản")
            @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Lọc theo vai trò")
            @RequestParam(required = false) UUID roleId,
            @Parameter(description = "Lọc theo lớp học (tạm thời chưa hỗ trợ trước T10)")
            @RequestParam(required = false) UUID classId,
            @Parameter(description = "Lọc tài khoản chưa từng đăng nhập")
            @RequestParam(required = false) Boolean neverLoggedIn,
            @Parameter(description = "Bao gồm tài khoản đã xóa mềm")
            @RequestParam(required = false) Boolean includeDeleted,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page,
                size,
                sort,
                UserAdminService.ALLOWED_SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return userAdminService.listUsers(
                q, source, status, roleId, classId, neverLoggedIn, includeDeleted, pageQuery);
    }

    @GetMapping("/username-availability")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Kiểm tra tính khả dụng và hợp lệ của tên đăng nhập")
    public UsernameAvailabilityResponse checkUsernameAvailability(
            @Parameter(description = "Tên đăng nhập cần kiểm tra")
            @RequestParam(required = false) String username) {
        return userAdminService.checkUsernameAvailability(username);
    }

    @GetMapping("/{id}")
    @RequirePermission(Permissions.USER_VIEW)
    @Operation(summary = "Chi tiết tài khoản người dùng")
    public UserDetailResponse getUser(@PathVariable UUID id) {
        return userAdminService.getUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Tạo tài khoản người dùng mới")
    public UserDetailResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userAdminService.createUser(request);
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Cập nhật thông tin tài khoản người dùng")
    public UserDetailResponse updateUser(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.updateUser(id, request);
    }
}
