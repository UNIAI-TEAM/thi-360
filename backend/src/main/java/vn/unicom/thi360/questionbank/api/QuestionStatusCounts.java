package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Số câu theo trạng thái với bộ lọc đang áp")
public record QuestionStatusCounts(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long draft,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long ready,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long retired) {}
