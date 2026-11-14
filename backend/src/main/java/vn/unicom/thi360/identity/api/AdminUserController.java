package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @Parameter(description = "Lọc theo lớp học")
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

    @GetMapping("/{id}/profile")
    @RequirePermission(Permissions.USER_VIEW)
    @Operation(summary = "Hồ sơ tài khoản gồm thông tin tài khoản, phương thức đăng nhập và lớp học")
    public UserProfileResponse getUserProfile(@PathVariable UUID id) {
        return userAdminService.getUserProfile(id);
    }

    @GetMapping("/{id}/learning")
    @RequirePermission(Permissions.USER_LEARNING_VIEW)
    @Operation(summary = "Dữ liệu hồ sơ học tập của học viên (từ module thi, rỗng khi chưa kết nối)")
    public StudentLearningResponse getUserLearning(@PathVariable UUID id) {
        return userAdminService.getUserLearning(id);
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

    @PostMapping("/{id}/deactivate")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Ngừng hoạt động tài khoản và thu hồi toàn bộ phiên")
    public UserDetailResponse deactivateUser(
            @PathVariable UUID id, @Valid @RequestBody DeactivateUserRequest request) {
        return userAdminService.deactivateUser(id, request);
    }

    @PostMapping("/{id}/activate")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Kích hoạt lại tài khoản đang ngừng hoạt động")
    public UserDetailResponse activateUser(@PathVariable UUID id) {
        return userAdminService.activateUser(id);
    }

    @PostMapping("/{id}/reset-password")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Đặt lại mật khẩu cho tài khoản và thu hồi toàn bộ phiên")
    public UserDetailResponse resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        return userAdminService.resetPassword(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Xóa mềm tài khoản (kiểm tra giao dịch qua AccountDeletionGuard)")
    public void deleteUser(
            @PathVariable UUID id,
            @Parameter(description = "Lý do xóa tài khoản (bắt buộc)", required = true)
            @RequestParam(required = false) String reason) {
        userAdminService.deleteUser(id, reason);
    }

    @GetMapping("/{id}/deletion-check")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Kiểm tra điều kiện xóa tài khoản")
    public DeletionCheckResponse checkDeletion(@PathVariable UUID id) {
        return userAdminService.checkDeletion(id);
    }

    @GetMapping("/{id}/deactivation-check")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Kiểm tra lượt thi đang làm trước khi ngừng hoạt động")
    public DeactivationCheckResponse checkDeactivation(@PathVariable UUID id) {
        return userAdminService.checkDeactivation(id);
    }

    @PostMapping("/bulk-actions/preview")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Xem trước thao tác hàng loạt trên nhiều tài khoản")
    public BulkUserActionPreviewResponse previewBulkActions(
            @Valid @RequestBody BulkUserActionRequest request) {
        return userAdminService.previewBulkActions(request);
    }

    @PostMapping("/bulk-actions")
    @RequirePermission(Permissions.USER_MODIFY)
    @Operation(summary = "Thực thi thao tác hàng loạt trên nhiều tài khoản (idempotent)")
    public BulkUserActionResponse applyBulkActions(
            @Parameter(description = "Khóa chống trùng lặp thao tác", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BulkUserActionRequest request) {
        return userAdminService.applyBulkActions(request, idempotencyKey);
    }
}
