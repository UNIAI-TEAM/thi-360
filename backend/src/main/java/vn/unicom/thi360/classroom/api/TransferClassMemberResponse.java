package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Kết quả chuyển học viên sang lớp học khác")
public record TransferClassMemberResponse(
        @Schema(description = "ID lớp nguồn", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID sourceClassId,
        @Schema(description = "ID lớp đích", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID targetClassId,
        @Schema(description = "ID học viên được chuyển", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Thời điểm chuyển lớp", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant transferredAt
) {
}
