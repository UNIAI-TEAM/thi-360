package vn.unicom.thi360.exam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;

class MapidRuleValidatorTest {
    private static final UUID NODE = UUID.randomUUID();
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void acceptsEveryRuleType() {
        var rules = List.of(
                rule("p", MapidRuleType.PRESENT, null, null, null, null),
                new MapidRule("c", MapidRuleType.COUNT, "g", List.of(NODE), MapidComparator.EXACT, 3, null),
                new MapidRule("cp", MapidRuleType.COUNT, null, List.of(NODE), MapidComparator.AT_LEAST, null,
                        new BigDecimal("20")),
                rule("o", MapidRuleType.ONLY_IN_GROUP, "g", null, null, null),
                new MapidRule("oc", MapidRuleType.ONLY_IN_GROUP, "g", List.of(NODE), MapidComparator.AT_LEAST, 0, null),
                rule("s", MapidRuleType.SOURCE, "g", null, null, null));
        assertThatCode(() -> ExamStructureValidator.validate(document(MapidMode.MATRIX, rules)))
                .doesNotThrowAnyException();
        assertThatCode(() -> ExamStructureValidator.validate(document(MapidMode.ALL, rules)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRulesWithoutMode() {
        assertRejected(document(null, List.of(rule("p", MapidRuleType.PRESENT, null, null, null, null))));
    }

    @Test
    void rejectsMissingOrDuplicateIds() {
        assertRejected(document(MapidMode.MATRIX, List.of(rule(null, MapidRuleType.PRESENT, null, null, null, null))));
        assertRejected(document(MapidMode.MATRIX, List.of(rule(" ", MapidRuleType.PRESENT, null, null, null, null))));
        assertRejected(document(MapidMode.MATRIX, List.of(
                rule("r", MapidRuleType.PRESENT, null, null, null, null),
                rule("r", MapidRuleType.SOURCE, null, null, null, null))));
    }

    @Test
    void rejectsNullRuleOrType() {
        List<MapidRule> withNull = new ArrayList<>();
        withNull.add(null);
        assertRejected(document(MapidMode.MATRIX, withNull));
        assertRejected(document(MapidMode.MATRIX, List.of(rule("r", null, null, null, null, null))));
    }

    @Test
    void rejectsEmptyOrNullNodeIds() {
        assertRejected(document(MapidMode.MATRIX, List.of(
                new MapidRule("r", MapidRuleType.PRESENT, null, List.of(), null, null, null))));
        assertRejected(document(MapidMode.MATRIX, List.of(
                new MapidRule("r", MapidRuleType.PRESENT, null, Arrays.asList(NODE, null), null, null, null))));
        assertRejected(document(MapidMode.MATRIX, List.of(
                new MapidRule("r", MapidRuleType.PRESENT, null, null, null, null, null))));
    }

    @Test
    void rejectsGroupThatIsNotABlockOfTheSection() {
        assertRejected(document(MapidMode.MATRIX, List.of(rule("r", MapidRuleType.SOURCE, "other", null, null, null))));
        assertRejected(document(MapidMode.ALL, List.of(rule("r", MapidRuleType.SOURCE, "other", null, null, null))));
    }

    @Test
    void onlyInGroupNeedsATargetGroup() {
        assertRejected(document(MapidMode.MATRIX, List.of(rule("r", MapidRuleType.ONLY_IN_GROUP, null, null, null, null))));
    }

    @Test
    void numbersAreRejectedWherePresentAndSourceHaveNone() {
        for (var type : List.of(MapidRuleType.PRESENT, MapidRuleType.SOURCE)) {
            assertRejected(document(MapidMode.MATRIX, List.of(rule("r", type, null, null, 1, null))));
            assertRejected(document(MapidMode.MATRIX, List.of(rule("r", type, null, null, null, BigDecimal.TEN))));
        }
    }

    @Test
    void countAndPercentRanges() {
        assertRejected(document(MapidMode.MATRIX, List.of(
                rule("r", MapidRuleType.COUNT, null, MapidComparator.EXACT, -1, null))));
        for (String percent : List.of("-0.1", "100.1")) {
            assertRejected(document(MapidMode.MATRIX, List.of(
                    rule("r", MapidRuleType.COUNT, null, MapidComparator.EXACT, null, new BigDecimal(percent)))));
        }
        for (String percent : List.of("0", "100")) {
            assertThatCode(() -> ExamStructureValidator.validate(document(MapidMode.MATRIX, List.of(
                    rule("r", MapidRuleType.COUNT, null, MapidComparator.EXACT, null, new BigDecimal(percent))))))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void countNeedsComparatorAndANumber() {
        assertRejected(document(MapidMode.MATRIX, List.of(rule("r", MapidRuleType.COUNT, null, null, 1, null))));
        assertRejected(document(MapidMode.MATRIX, List.of(
                rule("r", MapidRuleType.COUNT, null, MapidComparator.AT_LEAST, null, null))));
        assertRejected(document(MapidMode.MATRIX, List.of(rule("r", MapidRuleType.ONLY_IN_GROUP, "g", null, 2, null))));
    }

    @Test
    void limitsRulesPerSectionAndIsConfigurable() {
        List<MapidRule> rules = IntStream.range(0, 21)
                .mapToObj(i -> rule("r" + i, MapidRuleType.PRESENT, null, null, null, null)).toList();
        var document = document(MapidMode.MATRIX, rules);
        assertThatThrownBy(() -> ExamStructureValidator.validate(document))
                .isInstanceOfSatisfying(ExamStructureInvalidException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(ex.details()).extracting(ExamStructureInvalidException.Detail::field)
                            .containsExactly("sections.mapidRules");
                    assertThat(ex.details()).extracting(ExamStructureInvalidException.Detail::code)
                            .containsExactly("MAX_MAPID_RULES");
                });
        assertThatCode(() -> ExamStructureValidator.validate(document, ExamStructureValidator.DEFAULT_MAX_TOPIC_QUOTAS, 21))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ExamStructureValidator.validate(document(MapidMode.MATRIX, rules.subList(0, 3)),
                ExamStructureValidator.DEFAULT_MAX_TOPIC_QUOTAS, 2))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shapeErrorsReportTheMapidRulesField() {
        assertThatThrownBy(() -> ExamStructureValidator.validate(document(MapidMode.MATRIX,
                List.of(rule("r", MapidRuleType.PRESENT, "other", null, null, null)))))
                .isInstanceOfSatisfying(ExamStructureInvalidException.class, ex ->
                        assertThat(ex.details()).extracting(ExamStructureInvalidException.Detail::field)
                                .containsExactly("sections.mapidRules"));
    }

    @Test
    void jsonRoundTripKeepsModeAndRulesAndLegacySectionsStayUnchanged() {
        var rules = List.of(new MapidRule("c", MapidRuleType.COUNT, "g", List.of(NODE), MapidComparator.EXACT, 3,
                new BigDecimal("12.5")));
        var original = document(MapidMode.MATRIX, rules);
        String json = codec.writeString(original);
        assertThat(json).contains("\"mapidMode\":\"MATRIX\"", "\"mapidRules\"");
        assertThat(codec.read(json)).isEqualTo(original);

        String legacy = codec.writeString(document(null, List.of()));
        assertThat(legacy).doesNotContain("mapid");
        assertThat(codec.writeString(codec.read(legacy))).isEqualTo(legacy);
    }

    @Test
    void idRegenerationAndTemplateRestoreKeepRulesAndFollowTheGroupIds() {
        var original = document(MapidMode.MATRIX, List.of(
                rule("r", MapidRuleType.SOURCE, "g", null, null, null)));
        var regenerated = ExamStructureOps.regenerateIds(codec.read(codec.writeString(original)));
        ExamSection section = regenerated.parts().getFirst().sections().getFirst();
        assertThat(section.mapidMode()).isEqualTo(MapidMode.MATRIX);
        assertThat(section.mapidRules()).singleElement().satisfies(rule -> {
            assertThat(rule.id()).isEqualTo("r");
            assertThat(rule.groupId()).isEqualTo(section.blocks().getFirst().id()).isNotEqualTo("g");
        });

        var restored = ExamStructureOps.restoreTemplateOwned(original, original, null);
        assertThat(restored.parts().getFirst().sections().getFirst().mapidRules()).isEqualTo(
                original.parts().getFirst().sections().getFirst().mapidRules());
        assertThat(restored.parts().getFirst().sections().getFirst().mapidMode()).isEqualTo(MapidMode.MATRIX);
    }

    private static MapidRule rule(String id, MapidRuleType type, String groupId, MapidComparator comparator,
            Integer count, BigDecimal percent) {
        return new MapidRule(id, type, groupId, List.of(NODE), comparator, count, percent);
    }

    private static ExamStructureDocument document(MapidMode mode, List<MapidRule> rules) {
        var block = new QuestionBlock("g", "group", QuestionType.SHORT_ANSWER, 4, null, null, null, null, null, null,
                false, null, List.of(), null, true, false, List.of(), List.of(), null, List.of(), false, List.of(), null);
        var section = new ExamSection("s", "MATH", 4, null, null, null, null, List.of(block), List.of(), null, mode,
                rules);
        return new ExamStructureDocument(List.of(new ExamPart("p", 1, "part", null, 4, null, null, null, null,
                List.of(section), List.of())));
    }

    private static void assertRejected(ExamStructureDocument document) {
        assertThatThrownBy(() -> ExamStructureValidator.validate(document))
                .isInstanceOfSatisfying(ExamStructureInvalidException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(ex.details()).extracting(ExamStructureInvalidException.Detail::field)
                            .containsExactly("sections.mapidRules");
                });
    }
}
