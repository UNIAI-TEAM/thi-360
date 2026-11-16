package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamPart(
        String id,
        Integer order,
        String name,
        Integer durationMinutes,
        Integer questionCount,
        Integer questionCountOnPaper,
        Integer questionCountRequired,
        PartParticipation participation,
        BigDecimal maxPoints,
        List<ExamSection> sections,
        List<ExamPartOption> options) {
    public ExamPart {
        sections = sections == null ? List.of() : List.copyOf(sections);
        options = options == null ? List.of() : List.copyOf(options);
    }
}
