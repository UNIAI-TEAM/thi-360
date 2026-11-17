package vn.unicom.thi360.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
                "SCIENCE", "Khoa học", 3, List.of(17, 17, 16), subjects, List.of(), null);
        ExamPartOption english = new ExamPartOption("option-english", OptionKind.SUBJECT,
                "ENGLISH", "Tiếng Anh", null, null, List.of(), List.of(), null);
        ExamPart part = new ExamPart("part-1", 1, "choice", 50, 50, 50, 50,
                new PartParticipation(ParticipationMode.CHOICE, 1), null, List.of(), List.of(science, english));

        assertThat(ExamStructureValidator.countPaths(new ExamStructureDocument(List.of(part)))).isEqualTo(11);
    }

    @Test
    void includeDescendantsIsRejected() {
        KnowledgeScope removedField = new KnowledgeScope(List.of(UUID.randomUUID()), KnowledgeMatch.ANY, true, List.of());
        QuestionBlock block = new QuestionBlock("block", "single", QuestionType.SINGLE_CHOICE, 1,
                null, null, removedField, null, null, null, null, false, null, List.of(), null);
        ExamSection section = new ExamSection("section", "MATH", 1, null, null, null,
                null, List.of(block));
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
                "SCIENCE", null, 3, List.of(1, 1, 1), subjects, List.of(), null);
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
                    "SCIENCE-" + p, null, 3, List.of(1, 1, 1), subjects, List.of(), null);
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
                null, List.of(new QuestionBlock("block", "single", null, null, null, null,
                null, null, null, null, null, false, null, List.of(), null)));
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
                null, null, List.of(new QuestionBlock("block", "single", null, null, null, null,
                null, null, "SINGLE", null, "HSA_MATH_SINGLE", false, java.math.BigDecimal.ONE, List.of(), null)));
        ExamPart part = fixedPart("part", List.of(section));
        ExamStructureDocument original = new ExamStructureDocument(List.of(part));
        ExamStructureCodec codec = new ExamStructureCodec(MAPPER);

        ExamStructureDocument decoded = codec.read(codec.writeString(original));

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.parts().getFirst().sections().getFirst().answerQuotas().items()).hasSize(1);
    }

    @Test
    void b10ScienceChoiceGroupsAndSetCountsRoundTrip() {
        ExamSection physics = new ExamSection("physics", "LY", 17, null, null, null,
                ChoiceGroup.NATURAL, List.of());
        ExamSection history = new ExamSection("history", "SU", 16, null, null, null,
                ChoiceGroup.SOCIAL, List.of());
        QuestionBlock literatureSet = new QuestionBlock("lit-set", "Đọc ngữ liệu",
                QuestionType.QUESTION_SET, null, 5, 5, null, null, null, null, null,
                false, null, List.of(), null);
        ExamSection literature = new ExamSection("literature", "VAN", null, null, null, null,
                null, List.of(literatureSet));
        ExamStructureDocument document = new ExamStructureDocument(List.of(
                fixedPart("part", List.of(physics, history, literature))));

        ExamStructureDocument decoded = new ExamStructureCodec(MAPPER)
                .read(new ExamStructureCodec(MAPPER).writeString(document));

        assertThat(decoded.parts().getFirst().sections().get(0).choiceGroup()).isEqualTo(ChoiceGroup.NATURAL);
        assertThat(decoded.parts().getFirst().sections().get(1).choiceGroup()).isEqualTo(ChoiceGroup.SOCIAL);
        assertThat(decoded.parts().getFirst().sections().get(2).blocks().getFirst().setCount()).isEqualTo(5);
        assertThat(decoded.parts().getFirst().sections().get(2).blocks().getFirst().setSize()).isEqualTo(5);
    }

    @Test
    void rejectsFractionalMatrixAndAnswerQuotaValues() {
        BlockMatrix fractionalMatrix = new BlockMatrix(MatrixMode.SEPARATE, MatrixUnit.PERCENT,
                List.of(), List.of(new MatrixDimensionValue("G10", new BigDecimal("33.5"))),
                List.of(), List.of());
        AnswerQuotas fractionalAnswer = new AnswerQuotas(MatrixUnit.COUNT,
                List.of(new AnswerQuota(QuestionType.SINGLE_CHOICE, new BigDecimal("1.5"))));
        for (ExamSection section : List.of(
                new ExamSection("s1", "MATH", 1, null, null, fractionalMatrix, null, List.of()),
                new ExamSection("s2", "MATH", 1, null, fractionalAnswer, null, null, List.of()))) {
            assertThatThrownBy(() -> ExamStructureValidator.validate(
                    new ExamStructureDocument(List.of(fixedPart("p", List.of(section))))))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).getCode())
                    .isEqualTo("EXAM_STRUCTURE_INVALID");
        }
    }


    private static ExamPart fixedPart(String id, List<ExamSection> sections) {
        return new ExamPart(id, 1, "part", 1, null, null, null,
                new PartParticipation(ParticipationMode.ALL, null), null, sections, List.of());
    }

    private static List<ExamSection> sections(String... codes) {
        List<ExamSection> result = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            result.add(new ExamSection("section-" + codes[i], codes[i], null, null, null, null,
                    null, List.of()));
        }
        return result;
    }
}
