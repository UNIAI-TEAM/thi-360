package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;

@Schema(description = "Chi tiết câu hỏi: tóm tắt cộng nội dung của phiên bản đang xem")
public record QuestionDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionSummary summary,
        @Schema(description = "Đề bài HTML đã lọc; câu chùm: đoạn dẫn chung",
                requiredMode = Schema.RequiredMode.REQUIRED) String content,
        String solution,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> commonMistakes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> attachmentIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionBody body,
        @Schema(description = "Câu con của bộ (rỗng với dạng khác)",
                requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionDetail> children,
        @Schema(description = "Bản chụp đầy đủ của phiên bản đang xem") QuestionSnapshot snapshot) {}
