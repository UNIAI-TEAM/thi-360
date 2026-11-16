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
        Boolean expertConfirmed,
        List<ExamSection> sections,
        List<QuestionBlock> blocks) {
    public ExamPartOption {
        countsByPick = countsByPick == null ? null : List.copyOf(countsByPick);
        sections = sections == null ? List.of() : List.copyOf(sections);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
