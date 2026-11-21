package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu tạo mới bộ đề ở trạng thái nháp (TM 16.4)")
public record CreateBundleRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.bundle.code.notBlank}")
        @Size(max = 50, message = "{validation.bundle.code.size}")
        String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.bundle.name.notBlank}")
        @Size(max = 200, message = "{validation.bundle.name.size}")
        String name,

        @Schema(nullable = true)
        @Size(max = 300, message = "{validation.bundle.summary.size}")
        String summary,

        @Schema(nullable = true)
        @Size(max = 4000, message = "{validation.bundle.description.size}")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.bundle.examKindItemId.notNull}")
        UUID examKindItemId,

        @Schema(nullable = true)
        Long listPriceVnd,

        @Schema(nullable = true)
        Integer attemptsPerExam,

        @Schema(nullable = true)
        Boolean benefitAiExplanation,

        @Schema(nullable = true)
        Boolean benefitStrengthWeakness,

        @Schema(nullable = true)
        Boolean benefitPractice,

        @Schema(nullable = true)
        @Size(max = 1000, message = "{validation.bundle.benefitNote.size}")
        String benefitNote,

        @Schema(description = "SCORE, SCORE_AND_ANSWERS hoặc FULL_SOLUTION; mặc định SCORE_AND_ANSWERS", nullable = true)
        String resultView,

        @Schema(description = "Danh sách 2–12 bài thi trong bộ theo thứ tự", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.bundle.examIds.notNull}")
        List<UUID> examIds) {
}
