package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(name = "FinalizeEssayRequest", description = "Hoàn tất chấm câu tự luận (có thể gửi kèm điểm/tiêu chí/nhận xét cuối)")
public record FinalizeEssayRequest(
        Long expectedVersion,
        @Valid List<EssayCriterionInput> criteria,
        BigDecimal points,
        @Size(max = 10000, message = "{validation.grading.comment.size}") String comment) {}
