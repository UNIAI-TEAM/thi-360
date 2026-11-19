package vn.unicom.thi360.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

class ExamStructureV2Test {
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void v2DocumentRoundTripsAllStructureFields() {
        UUID node = UUID.randomUUID();
        TopicQuota quota = new TopicQuota("q", "Topic", List.of(node), 1, QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of("G12"), List.of("VD"), List.of("b"));
        QuestionBlock block = block(QuestionType.QUESTION_SET, false, List.of(new BlockSlot(1,
                new KnowledgeScope(List.of(node), List.of()), List.of("TAG"))), List.of(quota));
        BlockMatrix matrix = new BlockMatrix(MatrixMode.SEPARATE, MatrixUnit.COUNT, List.of(), List.of(),
                List.of(), List.of(), QuotaEnforcement.REFERENCE, QuotaEnforcement.REQUIRED, QuotaBasis.QUESTION);
        ExamSection section = new ExamSection("s", "MATH", 2, new KnowledgeScope(List.of(node), List.of()),
                null, matrix, null, List.of(block), List.of(quota));
        ExamPart part = new ExamPart("p", 1, "Part", null, 2, null, null, null, null,
                List.of(), List.of(), List.of(section), new CodeSource("GLOBAL_LIST", "SCIENCE"));
        ExamQuota examQuota = new ExamQuota("e", "Grade", ExamQuota.Dimension.GRADE,
                List.of(new ExamQuota.Bucket(List.of("G12"), 1, null)), QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of("MATH"), List.of());
        ExamStructureDocument original = new ExamStructureDocument(List.of(part), List.of(examQuota));
        assertThat(codec.read(codec.writeString(original))).isEqualTo(original);
        ExamStructureValidator.validate(original);
    }

    @Test
    void rejectsMismatchedCombinedEnforcements() {
        var matrix = new BlockMatrix(MatrixMode.COMBINED, MatrixUnit.COUNT, List.of(), List.of(), List.of(), List.of(),
                QuotaEnforcement.REFERENCE, QuotaEnforcement.REQUIRED, QuotaBasis.QUESTION);
        var section = new ExamSection("s", "MATH", 1, null, null, matrix, null, List.of());
        assertInvalid(new ExamStructureDocument(List.of(part(section))));
    }

    @Test
    void limitsTopicQuotasAtEachLevelAndReportsField() {
        List<TopicQuota> quotas = IntStream.range(0, 9).mapToObj(i -> new TopicQuota("q" + i, "q", List.of(), 1,
                QuotaBasis.QUESTION, QuotaEnforcement.REQUIRED, List.of(), List.of(), List.of())).toList();
        var section = new ExamSection("s", "MATH", 1, null, null, null, null, List.of(), quotas);
        var document = new ExamStructureDocument(List.of(part(section)));
        assertThatThrownBy(() -> ExamStructureValidator.validate(document))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(ex.getFieldErrors()).extracting("field").containsExactly("topicQuotas");
                });
        ExamStructureValidator.validate(document, 9);
        var block = block(QuestionType.QUESTION_SET, false, List.of(), quotas);
        assertInvalid(new ExamStructureDocument(List.of(part(new ExamSection("s", "MATH", 2,
                null, null, null, null, List.of(block))))));
    }

    @Test
    void rejectsOutOfRangeSlotsAndOmittedNonEssay() {
        for (int index : List.of(0, 3)) {
            assertInvalid(document(block(QuestionType.QUESTION_SET, false,
                    List.of(new BlockSlot(index, null, List.of())), List.of())));
        }
        assertInvalid(document(block(QuestionType.SINGLE_CHOICE, true, List.of(), List.of())));
    }

    @Test
    void rejectsRequiredExamQuotaAcrossSubjects() {
        var quota = new ExamQuota("e", "grade", ExamQuota.Dimension.GRADE, List.of(), QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of("MATH", "PHYSICS"), List.of());
        assertThatThrownBy(() -> ExamStructureValidator.validate(new ExamStructureDocument(List.of(), List.of(quota))))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EXAM_QUOTA_SCOPE_INVALID"));
    }

    @Test
    void rejectsLegacyMatchAndCombinationOption() {
        for (String json : List.of(
                "{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"subjectCode\":\"MATH\",\"knowledgeScope\":{\"nodeIds\":[],\"match\":\"ANY\"}}]}]}",
                "{\"parts\":[{\"id\":\"p\",\"options\":[{\"id\":\"o\",\"kind\":\"COMBINATION\",\"code\":\"X\"}]}]}")) {
            assertInvalid(codec.read(json));
        }
    }

    @Test
    void topicPercentRangeReportsSectionAndBlockFields() {
        for (String value : List.of("-1", "101")) {
            var quota = new TopicQuota("q", "topic", List.of(), 1, QuotaBasis.QUESTION,
                    QuotaEnforcement.REQUIRED, List.of(), List.of(), List.of(), new BigDecimal(value));
            var section = new ExamSection("s", "MATH", 2, null, null, null, null, List.of(), List.of(quota));
            assertThatThrownBy(() -> ExamStructureValidator.validate(new ExamStructureDocument(List.of(part(section)))))
                    .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("sections.topicQuotas");
                    });
            assertThatThrownBy(() -> ExamStructureValidator.validate(document(block(QuestionType.QUESTION_SET,
                    false, List.of(), List.of(quota)))))
                    .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                        assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                        assertThat(ex.getFieldErrors()).extracting("field").containsExactly("blocks.topicQuotas");
                    });
        }
    }

    @Test
    void newFieldsSurviveIdRegenerationAndOldDocumentsRemainUnchanged() {
        var quota = new TopicQuota("q", "topic", List.of(), 1, QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of(), List.of(), List.of("b"), new BigDecimal("12.5"));
        var scope = new KnowledgeScope(List.of(), List.of(), true);
        var section = new ExamSection("s", "MATH", 2, scope, null, null, null,
                List.of(block(QuestionType.QUESTION_SET, false, List.of(), List.of(quota))), List.of(quota));
        var original = new ExamStructureDocument(List.of(part(section)));
        var regenerated = ExamStructureOps.regenerateIds(codec.read(codec.writeString(original)));
        var savedSection = codec.read(codec.writeString(regenerated)).parts().getFirst().sections().getFirst();
        assertThat(savedSection.knowledgeScope().everyChildInScope()).isTrue();
        assertThat(savedSection.topicQuotas().getFirst().percent()).isEqualByComparingTo("12.5");
        assertThat(savedSection.blocks().getFirst().topicQuotas().getFirst().percent()).isEqualByComparingTo("12.5");
        assertThat(savedSection.topicQuotas().getFirst().groupIds())
                .containsExactly(savedSection.blocks().getFirst().id());

        var oldScope = new KnowledgeScope(List.of(), List.of());
        var oldQuota = new TopicQuota("q", "topic", List.of(), 1, QuotaBasis.QUESTION,
                QuotaEnforcement.REQUIRED, List.of(), List.of(), List.of());
        var oldSection = new ExamSection("s", "MATH", 2, oldScope, null, null, null, List.of(), List.of(oldQuota));
        String oldJson = codec.writeString(new ExamStructureDocument(List.of(part(oldSection))));
        assertThat(codec.writeString(codec.read(oldJson))).isEqualTo(oldJson)
                .doesNotContain("everyChildInScope", "percent");
        assertThat(oldScope).isNotEqualTo(scope);
    }

    private static QuestionBlock block(QuestionType type, boolean omitted, List<BlockSlot> slots,
            List<TopicQuota> quotas) {
        return new QuestionBlock("b", "block", type, null, 2, 1, null, null, null, null, false,
                null, List.of(), null, true, false, slots, List.of(new ChildCondition(1, 1, List.of())),
                new ItemShape(4, null, null), List.of("INTEGER"), omitted, quotas, null);
    }

    private static ExamStructureDocument document(QuestionBlock block) {
        return new ExamStructureDocument(List.of(part(new ExamSection("s", "MATH", 2, null, null, null,
                null, List.of(block)))));
    }

    private static ExamPart part(ExamSection section) {
        return new ExamPart("p", 1, "part", null, 2, null, null, null, null, List.of(section), List.of());
    }

    private static void assertInvalid(ExamStructureDocument document) {
        assertThatThrownBy(() -> ExamStructureValidator.validate(document))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID"));
    }
}
