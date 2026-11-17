package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Cập nhật tổng quan bài thi")
public record ExamOverviewRequest(@NotNull List<Row> overview, @NotNull Long expectedRevision) {
    @Schema(name = "ExamOverviewRow")
    public record Row(String partId, String sectionId, String partCode, String subjectCode,
                      Integer questionCount, Integer durationMinutes, BigDecimal maxPoints) { }
}
