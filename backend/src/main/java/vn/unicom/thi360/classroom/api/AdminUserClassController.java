package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.classroom.application.ClassService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin User Classes", description = "Tra cứu lớp học hiện tại và lịch sử lớp của tài khoản")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserClassController {

    private final ClassService classService;

    public AdminUserClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping("/{id}/classes")
    @RequirePermission(Permissions.CLASS_VIEW)
    @Operation(summary = "Danh sách lớp hiện tại và lịch sử tham gia, rời, chuyển lớp của tài khoản")
    public UserClassesResponse getUserClasses(@PathVariable UUID id) {
        return classService.getUserClasses(id);
    }
}
