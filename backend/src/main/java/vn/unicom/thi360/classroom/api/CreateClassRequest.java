package vn.unicom.thi360.classroom.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import vn.unicom.thi360.classroom.domain.ClassNormalizer;
import vn.unicom.thi360.classroom.domain.ClassStatus;

@Schema(description = "Yêu cầu tạo lớp học mới")
public record CreateClassRequest(
        @Schema(
                description = "Mã lớp học (2-30 ký tự chữ không dấu, số, '.', '_', '-', bắt đầu bằng chữ hoặc số)",
                example = "LOP-A",
                minLength = 2,
                maxLength = 30,
                pattern = ClassNormalizer.CODE_REGEX,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.class.code.notBlank}")
        String code,
        @Schema(description = "Tên lớp học", example = "Lớp A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.class.name.notBlank}")
        @Size(max = 200, message = "{validation.class.name.size}")
        String name,
        @Schema(description = "Mô tả lớp học")
        @Size(max = 1000, message = "{validation.class.description.size}")
        String description,
        @Schema(description = "ID người phụ trách lớp (không bắt buộc)")
        UUID ownerUserId,
        @Schema(description = "Trạng thái ban đầu (mặc định ACTIVE)")
        ClassStatus status
) {
}
