package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(name = "SaveEssayDraftRequest", description = "Lưu nháp điểm chấm tự luận kèm khóa lạc quan")
public record SaveEssayDraftRequest(
        @Valid List<EssayCriterionInput> criteria,
        BigDecimal points,
        @Size(max = 10000, message = "{validation.grading.comment.size}") String comment,
        @NotNull(message = "{validation.grading.expectedVersion.notNull}") Long expectedVersion) {}
