package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Kết quả thêm học viên vào lớp học")
public record AddClassMembersResponse(
        @Schema(description = "ID lớp học", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classId,
        @Schema(description = "Danh sách ID học viên đã được thêm mới", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UUID> addedUserIds,
        @Schema(description = "Danh sách ID học viên bị bỏ qua do đã thuộc lớp hoặc trùng lặp", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UUID> skippedUserIds,
        @Schema(description = "Số lượng học viên thêm mới thành công", requiredMode = Schema.RequiredMode.REQUIRED)
        int addedCount,
        @Schema(description = "Số lượng bị bỏ qua do trùng lặp", requiredMode = Schema.RequiredMode.REQUIRED)
        int skippedCount
) {
}
