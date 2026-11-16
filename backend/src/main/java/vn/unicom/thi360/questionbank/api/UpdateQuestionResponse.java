package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả lưu câu hỏi")
public record UpdateQuestionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionDetail question,
        @Schema(description = "true khi lần lưu tạo phiên bản mới (câu đã Sẵn sàng hoặc Ngừng)",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean versionCreated) {}
