package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu thêm học viên vào lớp học")
public record AddClassMembersRequest(
        @Schema(description = "Danh sách ID tài khoản học viên cần thêm vào lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "{validation.classMember.userIds.notEmpty}")
        List<UUID> userIds
) {
}
