package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AnswerValue;

/** Bài làm tạm của admin trong phiên xem trước để chấm không lưu DB (LT §10.2, F20). */
@Schema(name = "PreviewGradeRequest")
public record PreviewGradeRequest(
        @Schema(description = "Bản đồ câu trả lời theo questionId (có thể rỗng hoặc bỏ sót câu bỏ trống)")
        Map<UUID, AnswerValue> answers) {}
