package vn.unicom.thi360.classroom.api;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.classroom.application.ClassService;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Classes", description = "Quản lý lớp học và thành viên lớp")
@RestController
@RequestMapping("/api/v1/admin/classes")
public class AdminClassController {

    private final ClassService classService;

    public AdminClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    @RequirePermission(Permissions.CLASS_VIEW)
    @Operation(summary = "Danh sách lớp học")
    public PageResponse<ClassSummaryResponse> listClasses(
            @Parameter(description = "Tìm kiếm theo mã, tên, mô tả lớp")
            @RequestParam(required = false) String q,
            @Parameter(description = "Lọc theo trạng thái lớp")
            @RequestParam(required = false) ClassStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page,
                size,
                sort,
                ClassService.ALLOWED_CLASS_SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return classService.listClasses(q, status, pageQuery);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Tạo lớp học mới")
    public ClassDetailResponse createClass(@Valid @RequestBody CreateClassRequest request) {
        return classService.createClass(request);
    }

    @GetMapping("/{id}")
    @RequirePermission(Permissions.CLASS_VIEW)
    @Operation(summary = "Chi tiết lớp học")
    public ClassDetailResponse getClass(@PathVariable UUID id) {
        return classService.getClass(id);
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Cập nhật thông tin lớp học")
    public ClassDetailResponse updateClass(
            @PathVariable UUID id, @Valid @RequestBody UpdateClassRequest request) {
        return classService.updateClass(id, request);
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Cập nhật trạng thái hoạt động của lớp học")
    public ClassDetailResponse updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateClassStatusRequest request) {
        return classService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Xóa mềm lớp học (không ảnh hưởng tài khoản học viên)")
    public void deleteClass(
            @PathVariable UUID id,
            @Parameter(description = "Lý do xóa lớp (không bắt buộc)")
            @RequestParam(required = false) String reason) {
        classService.deleteClass(id, reason);
    }

    @GetMapping("/{id}/members")
    @RequirePermission(Permissions.CLASS_VIEW)
    @Operation(summary = "Danh sách học viên trong lớp")
    public PageResponse<ClassMemberResponse> listMembers(
            @PathVariable UUID id,
            @Parameter(description = "Tìm kiếm theo mã, tên đăng nhập, họ tên, email, số điện thoại")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page,
                size,
                sort,
                ClassService.ALLOWED_MEMBER_SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "joinedAt"));
        return classService.listMembers(id, q, pageQuery);
    }

    @PostMapping("/{id}/members")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Thêm học viên vào lớp (bỏ qua và báo các ID trùng lặp)")
    public AddClassMembersResponse addMembers(
            @PathVariable UUID id, @Valid @RequestBody AddClassMembersRequest request) {
        return classService.addMembers(id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Loại học viên khỏi lớp học")
    public void removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Parameter(description = "Lý do loại khỏi lớp (không bắt buộc)")
            @RequestParam(required = false) String reason) {
        classService.removeMember(id, userId, reason);
    }

    @PostMapping("/{id}/members/{userId}/transfer")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Chuyển học viên sang lớp học khác trong một giao dịch")
    public TransferClassMemberResponse transferMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody TransferClassMemberRequest request) {
        return classService.transferMember(id, userId, request);
    }
}
