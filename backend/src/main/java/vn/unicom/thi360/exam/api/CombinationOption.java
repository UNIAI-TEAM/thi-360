package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Mã tổ hợp môn gắn với một kỳ thi")
public record CombinationOption(
        @Schema(description = "Mã tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Mã các môn trong tổ hợp", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> subjectCodes,
        @Schema(description = "Mã kỳ thi dùng tổ hợp này", requiredMode = Schema.RequiredMode.REQUIRED)
        String examKindCode
) {
    public CombinationOption {
        subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
    }
}
