package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mã bài thi gợi ý theo quy ước <KIND>-<F|P|S>-[PHẦN|MÔN-]<seq:03d>")
public record SuggestExamCodeResponse(
        @Schema(example = "HSA-S-TOAN-004", requiredMode = Schema.RequiredMode.REQUIRED)
        String code
) {
}
