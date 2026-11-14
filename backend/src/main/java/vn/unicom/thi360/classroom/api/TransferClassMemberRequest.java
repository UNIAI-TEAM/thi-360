package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Yêu cầu chuyển học viên sang lớp học khác")
public record TransferClassMemberRequest(
        @Schema(description = "ID lớp học đích", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Lớp đích là bắt buộc")
        UUID targetClassId,
        @Schema(description = "Lý do chuyển lớp")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
