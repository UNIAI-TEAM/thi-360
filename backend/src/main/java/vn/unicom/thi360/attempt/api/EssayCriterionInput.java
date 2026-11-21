package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "EssayCriterionInput", description = "Điểm và nhận xét theo một tiêu chí chấm tự luận")
public record EssayCriterionInput(
        @Size(max = 100, message = "{validation.grading.criterionId.size}") String id,
        BigDecimal points,
        @Size(max = 2000, message = "{validation.grading.criterionComment.size}") String comment) {}
