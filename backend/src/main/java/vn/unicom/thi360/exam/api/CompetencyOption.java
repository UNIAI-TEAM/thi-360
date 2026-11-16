package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Một năng lực (danh mục COMPETENCY): gắn một môn và các kỳ thi liên kết")
public record CompetencyOption(
        @Schema(description = "Mã năng lực", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mã môn của năng lực; null khi chưa gắn")
        String subjectCode,
        @Schema(description = "Mã các kỳ thi liên kết", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> examKindCodes
) {
    public CompetencyOption {
        examKindCodes = examKindCodes == null ? List.of() : List.copyOf(examKindCodes);
    }
}
