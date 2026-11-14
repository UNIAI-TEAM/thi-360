package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.unicom.thi360.classroom.domain.ClassNormalizer;

@Schema(description = "Yêu cầu cập nhật thông tin lớp học")
public record UpdateClassRequest(
        @Schema(
                description = "Mã lớp học mới (2-30 ký tự chữ không dấu, số, '.', '_', '-', bắt đầu bằng chữ hoặc số)",
                example = "LOP-A",
                minLength = 2,
                maxLength = 30,
                pattern = ClassNormalizer.CODE_REGEX)
        String code,
        @Schema(description = "Tên lớp học mới")
        @Size(max = 200, message = "{validation.class.name.size}")
        String name,
        @Schema(description = "Mô tả lớp học mới (chuỗi rỗng để xóa mô tả)")
        @Size(max = 1000, message = "{validation.class.description.size}")
        String description,
        @Schema(description = "ID người phụ trách mới")
        UUID ownerUserId,
        @Schema(description = "Xóa người phụ trách hiện tại khi đặt là true")
        Boolean clearOwner,
        @Schema(description = "Phiên bản khóa lạc quan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.version.notNull}")
        Long version
) {
}
