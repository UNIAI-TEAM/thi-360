package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một dạng câu và số câu từng dạng trong một phần (TM 10.1)")
public record AnswerTypeGroup(
        @Schema(description = "SINGLE_CHOICE, SHORT_ANSWER, QUESTION_SET, ESSAY, ...",
                requiredMode = Schema.RequiredMode.REQUIRED) String questionType,
        @Schema(description = "Số câu của dạng này (một bộ chùm tính câu con)",
                requiredMode = Schema.RequiredMode.REQUIRED) int questionCount,
        @Schema(description = "Dạng câu con của một bộ chùm; null khi không phải chùm một dạng",
                nullable = true) String childAnswerType) {
}
