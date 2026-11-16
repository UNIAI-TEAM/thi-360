package vn.unicom.thi360.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

class ExamStructureValidatorTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void subjectChoiceCountsEachPickAsAPath() {
        List<ExamSection> subjects = sections("MATH", "VAN", "ANH", "LY", "HOA");
        ExamPartOption science = new ExamPartOption("option-science", OptionKind.SUBJECT_CHOICE,
                "SCIENCE", "Khoa học", 3, List.of(17, 17, 16), false, subjects, List.of());
        ExamPartOption english = new ExamPartOption("option-english", OptionKind.SUBJECT,
                "ENGLISH", "Tiếng Anh", null, null, null, List.of(), List.of());
        ExamPart part = new ExamPart("part-1", 1, "choice", 50, 50, 50, 50,
                new PartParticipation(ParticipationMode.CHOICE, 1), null, List.of(), List.of(science, english));

        assertThat(ExamStructureValidator.countPaths(new ExamStructureDocument(List.of(part)))).isEqualTo(11);
    }

    @Test
    void includeDescendantsIsRejected() {
        KnowledgeScope removedField = new KnowledgeScope(List.of(UUID.randomUUID()), KnowledgeMatch.ANY, true, List.of());
        QuestionBlock block = new QuestionBlock("block", "single", QuestionType.SINGLE_CHOICE, 1,
                null, null, removedField, null, null, null, null, false, null, null);
        ExamSection section = new ExamSection("section", "MATH", 1, null, null, null,
                GroupOrder.FIXED, List.of(block));
        ExamPart part = fixedPart("part", List.of(section));

        assertThatThrownBy(() -> ExamStructureValidator.validate(new ExamStructureDocument(List.of(part))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("EXAM_STRUCTURE_INVALID");
    }

    @Test
    void subjectChoiceWithMoreThanEightSubjectsIsRejected() {
        List<ExamSection> subjects = sections("A", "B", "C", "D", "E", "F", "G", "H", "I");
        ExamPartOption choice = new ExamPartOption("choice", OptionKind.SUBJECT_CHOICE,
                "SCIENCE", null, 3, List.of(1, 1, 1), false, subjects, List.of());
        ExamPart part = new ExamPart("part", 1, "p", 9, 9, 9, 9,
                new PartParticipation(ParticipationMode.CHOICE, 1), null, List.of(), List.of(choice));

        assertThatThrownBy(() -> ExamStructureValidator.validate(new ExamStructureDocument(List.of(part))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("EXAM_STRUCTURE_INVALID");
    }

    @Test
    void moreThanTwoHundredPathsIsRejected() {
        List<ExamPart> parts = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            List<ExamSection> subjects = sections("A" + p, "B" + p, "C" + p, "D" + p, "E" + p);
            ExamPartOption choice = new ExamPartOption("choice-" + p, OptionKind.SUBJECT_CHOICE,
                    "SCIENCE-" + p, null, 3, List.of(1, 1, 1), false, subjects, List.of());
            parts.add(new ExamPart("part-" + p, p + 1, "p", 1, 1, 1, 1,
                    new PartParticipation(ParticipationMode.CHOICE, 1), null, List.of(), List.of(choice)));
        }

        assertThatThrownBy(() -> ExamStructureValidator.validate(new ExamStructureDocument(parts)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("EXAM_PATH_LIMIT_EXCEEDED");
    }

    @Test
    void draftMayOmitQuestionCountsButIdsMustBeUnique() {
        ExamSection section = new ExamSection("section", "MATH", null, null, null, null,
                GroupOrder.FIXED, List.of(new QuestionBlock("block", "single", null, null, null, null,
                null, null, null, null, null, false, null, null)));
        ExamPart part = fixedPart("part", List.of(section));
        ExamStructureValidator.validate(new ExamStructureDocument(List.of(part)));

        ExamPart duplicate = fixedPart("part", List.of());
        assertThatThrownBy(() -> ExamStructureValidator.validate(new ExamStructureDocument(List.of(part, duplicate))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("EXAM_STRUCTURE_INVALID");
    }

    @Test
    void jsonCodecRoundTripsB9Fields() {
        KnowledgeScope scope = new KnowledgeScope(List.of(UUID.randomUUID()), KnowledgeMatch.ALL, null, List.of("VACT-TOAN"));
        ExamSection section = new ExamSection("section", "MATH", 50, scope,
                new AnswerQuotas(MatrixUnit.COUNT, List.of(new AnswerQuota(QuestionType.SINGLE_CHOICE, java.math.BigDecimal.valueOf(35)))),
                null, GroupOrder.FIXED, List.of(new QuestionBlock("block", "single", null, null, null, null,
                null, null, "SINGLE", null, "HSA_MATH_SINGLE", false, java.math.BigDecimal.ONE, null)));
        ExamPart part = fixedPart("part", List.of(section));
        ExamStructureDocument original = new ExamStructureDocument(List.of(part));
        ExamStructureCodec codec = new ExamStructureCodec(MAPPER);

        ExamStructureDocument decoded = codec.read(codec.writeString(original));

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.parts().getFirst().sections().getFirst().answerQuotas().items()).hasSize(1);
    }

    private static ExamPart fixedPart(String id, List<ExamSection> sections) {
        return new ExamPart(id, 1, "part", 1, null, null, null,
                new PartParticipation(ParticipationMode.ALL, null), null, sections, List.of());
    }

    private static List<ExamSection> sections(String... codes) {
        List<ExamSection> result = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            result.add(new ExamSection("section-" + codes[i], codes[i], null, null, null, null,
                    GroupOrder.FIXED, List.of()));
        }
        return result;
    }
}
