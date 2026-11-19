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
        List<ExamPartOption> options,
        List<ExamSection> subjectPool,
        CodeSource codeSource,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DisplaySegment> displaySegments) {
    public ExamPart {
        sections = sections == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(sections));
        options = options == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(options));
        subjectPool = subjectPool == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(subjectPool));
        displaySegments = displaySegments == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(displaySegments));
    }

    public ExamPart(String id, Integer order, String name, Integer durationMinutes,
            Integer questionCount, Integer questionCountOnPaper, Integer questionCountRequired,
            PartParticipation participation, BigDecimal maxPoints, List<ExamSection> sections,
            List<ExamPartOption> options, List<ExamSection> subjectPool, CodeSource codeSource) {
        this(id, order, name, durationMinutes, questionCount, questionCountOnPaper,
                questionCountRequired, participation, maxPoints, sections, options, subjectPool,
                codeSource, List.of());
    }

    public ExamPart(String id, Integer order, String name, Integer durationMinutes,
            Integer questionCount, Integer questionCountOnPaper, Integer questionCountRequired,
            PartParticipation participation, BigDecimal maxPoints, List<ExamSection> sections,
            List<ExamPartOption> options) {
        this(id, order, name, durationMinutes, questionCount, questionCountOnPaper,
                questionCountRequired, participation, maxPoints, sections, options, List.of(), null,
                List.of());
    }
}
