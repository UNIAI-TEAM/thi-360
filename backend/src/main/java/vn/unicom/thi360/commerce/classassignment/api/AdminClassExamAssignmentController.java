package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.classassignment.application.ClassExamAssignmentService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Classes", description = "Quản lý lớp học và giao bài thi theo lớp")
@RestController
@RequestMapping("/api/v1/admin/classes/{classId}")
public class AdminClassExamAssignmentController {

    private final ClassExamAssignmentService assignmentService;

    public AdminClassExamAssignmentController(ClassExamAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/exam-assignments")
    @RequirePermission(Permissions.CLASS_VIEW)
    @Operation(summary = "Danh sách các đợt giao bài thi của lớp học")
    public List<ClassExamAssignmentItem> listAssignments(@PathVariable UUID classId) {
        return assignmentService.listAssignments(classId);
    }

    @GetMapping("/assignable-exams")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Danh sách các bài thi đang xuất bản có thể giao cho lớp học")
    public List<AssignableExamItem> listAssignableExams(@PathVariable UUID classId) {
        return assignmentService.listAssignableExams(classId);
    }

    @PostMapping("/exam-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Giao một hoặc nhiều bài thi đang xuất bản cho lớp học")
    public AssignClassExamsResponse assignExams(
            @PathVariable UUID classId,
            @Parameter(description = "Khóa chống gửi trùng (bắt buộc)")
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody AssignClassExamsRequest request) {
        return assignmentService.assignExams(classId, idempotencyKey, request);
    }

    @PatchMapping("/exam-assignments/{assignmentId}")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Cập nhật cấu hình tự động cấp thành viên mới của đợt giao bài")
    public ClassExamAssignmentItem updateAssignment(
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateClassExamAssignmentRequest request) {
        return assignmentService.updateAssignment(classId, assignmentId, request);
    }

    @PostMapping("/exam-assignments/{assignmentId}/revoke")
    @RequirePermission(Permissions.CLASS_MODIFY)
    @Operation(summary = "Thu hồi đợt giao bài thi của lớp và thu hồi các lượt chưa dùng")
    public RevokeClassExamAssignmentResponse revokeAssignment(
            @PathVariable UUID classId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody(required = false) RevokeClassExamAssignmentRequest request) {
        return assignmentService.revokeAssignment(classId, assignmentId, request);
    }
}
