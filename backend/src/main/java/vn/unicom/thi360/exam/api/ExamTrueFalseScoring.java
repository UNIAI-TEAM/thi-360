package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * OpenAPI shape of the stored multi-statement True/False scoring ({@code exam_kind_profile.tf_scoring}). The profile
 * keeps the document as JSON; this record only gives that JSON an explicit type in the contract (FU-QA-OPENAPI-TYPES).
 */
@Schema(name = "ExamTrueFalseScoring",
        description = "PER_STATEMENT: statementPoints per correct statement; COUNT_TABLE: table[i] is the score for i+1 correct statements")
public record ExamTrueFalseScoring(
        @Schema(allowableValues = {"PER_STATEMENT", "COUNT_TABLE"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String method,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<BigDecimal> table,
        @Schema(types = {"number", "null"})
        BigDecimal statementPoints) {
}
