package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Tổng hợp cấu trúc theo phần, môn và nhóm")
public record ExamStructureSummaryResponse(List<Row> parts) {
    @Schema(name = "ExamStructureSummaryRow")
    public record Row(String partId, String sectionId, String blockId, String name,
                      Integer target, Integer allocated, Integer difference,
                      Integer setCount, Integer childCount, Integer commandCount,
                      BigDecimal maxPoints, Integer essayCount, Integer objectiveCount,
                      List<Row> children) { }
}
