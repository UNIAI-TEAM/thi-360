package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Lịch sử phiên bản của câu, mới nhất trước")
public record QuestionVersionList(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionVersionItem> versions) {}
