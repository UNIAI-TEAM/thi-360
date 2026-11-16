package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExamPartOption(
        String id,
        OptionKind kind,
        String code,
        String name,
        Integer chooseSubjects,
        List<Integer> countsByPick,
        List<ExamSection> sections,
        List<QuestionBlock> blocks,
        Boolean expertConfirmed) {
    public ExamPartOption {
        countsByPick = countsByPick == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(countsByPick));
        sections = sections == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(sections));
        blocks = blocks == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(blocks));
    }
}
