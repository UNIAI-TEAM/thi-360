package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamPreviewController.Compliance;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class PreviewComplianceTest {
    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    @Test
    void selectedOptionBlockIncludesReferenceMatrix() {
        var document = codec.read("""
                {"parts":[{"id":"part","participation":{"mode":"CHOICE","choose":1},
                  "options":[{"id":"option","kind":"SUBJECT","code":"EN","blocks":[
                    {"id":"block","questionType":"SINGLE_CHOICE","questionCount":1,
                     "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE",
                       "byGrade":[{"code":"G12","value":1}]}}]}]}]}
                """);
        UUID id = UUID.randomUUID();
        var section = new SectionOnPath("EN", "option:blocks", 1, null, List.of(GroupQuota.single("block", 1)));
        var rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("block", new GroupWitness("block", List.of(new QuestionPick(id.toString(), 1)))),
                Map.of(new QuestionSourcePort.VersionKey(id, 1), candidate(id, "G12", List.of())),
                List.of(), List.of());
        assertThat(rows).contains(new Compliance("GRADE:G12", "BLOCK:block", BigDecimal.ONE, BigDecimal.ONE,
                "REFERENCE", "QUESTION", null));
    }

    @Test
    void referenceGradeUsesFullQuotaLessDroppedContribution() {
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"english","subjectCode":"EN","questionCount":2,
                  "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE",
                    "byGrade":[{"code":"G11","value":1},{"code":"G12","value":1}]},
                  "blocks":[{"id":"group","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        UUID kept = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        var keepCandidate = candidate(kept, "G11", List.of());
        var dropCandidate = candidate(removed, "G12", List.of());
        var pick = new QuestionPick(kept.toString(), 1);
        var drop = new PreviewSnapshotStore.Dropped(new QuestionPick(removed.toString(), 1), "EN", "english");
        var section = new SectionOnPath("EN", "english", 1, null, List.of(GroupQuota.single("group", 2)));

        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("group", new GroupWitness("group", List.of(pick))),
                Map.of(new QuestionSourcePort.VersionKey(kept, 1), keepCandidate,
                        new QuestionSourcePort.VersionKey(removed, 1), dropCandidate), List.of(drop), List.of());
        assertThat(rows).contains(
                new Compliance("GRADE:G11", "SECTION:english", BigDecimal.ONE, BigDecimal.ONE,
                        "REFERENCE", "QUESTION", "dropped 1"),
                new Compliance("GRADE:G12", "SECTION:english", BigDecimal.ZERO, BigDecimal.ZERO,
                        "REFERENCE", "QUESTION", "dropped 1"));
    }

    @Test
    void droppedQuestionIsAttributedToItsSectionWhenSubjectsRepeat() {
        var document = codec.read("""
                {"parts":[
                  {"id":"p1","sections":[{"id":"first","subjectCode":"EN","questionCount":1,
                    "blocks":[{"id":"g1","questionType":"SINGLE_CHOICE","questionCount":1}]}]},
                  {"id":"p2","sections":[{"id":"second","subjectCode":"EN","questionCount":2,
                    "matrix":{"mode":"SEPARATE","unit":"COUNT","gradeEnforcement":"REFERENCE",
                              "byGrade":[{"code":"G12","value":1}]},
                    "blocks":[{"id":"g2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """);
        UUID first = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        var firstSection = new SectionOnPath("EN", "first", 1, null, List.of(GroupQuota.single("g1", 1)));
        var secondSection = new SectionOnPath("EN", "second", 1, null, List.of(GroupQuota.single("g2", 2)));
        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(firstSection, secondSection)),
                Map.of("g1", new GroupWitness("g1", List.of(new QuestionPick(first.toString(), 1))),
                        "g2", new GroupWitness("g2", List.of(new QuestionPick(kept.toString(), 1)))),
                Map.of(new QuestionSourcePort.VersionKey(first, 1), candidate(first, "G11", List.of()),
                        new QuestionSourcePort.VersionKey(kept, 1), candidate(kept, "G11", List.of()),
                        new QuestionSourcePort.VersionKey(removed, 1), candidate(removed, "G12", List.of())),
                List.of(new PreviewSnapshotStore.Dropped(new QuestionPick(removed.toString(), 1), "EN", "second")),
                List.of());
        assertThat(rows).contains(new Compliance("GRADE:G12", "SECTION:second", BigDecimal.ZERO, BigDecimal.ZERO,
                "REFERENCE", "QUESTION", "dropped 1"));
    }

    @Test
    void overlappingTopicQuotasReportIndependentCounts() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"math","subjectCode":"MATH","questionCount":3,
                  "topicQuotas":[{"id":"a","nodeIds":["%s"],"count":1},
                                 {"id":"b","nodeIds":["%s"],"count":1}],
                  "blocks":[{"id":"group","questionType":"SINGLE_CHOICE","questionCount":3}]}]}]}
                """.formatted(a, b));
        UUID both = UUID.randomUUID();
        UUID onlyA = UUID.randomUUID();
        UUID anotherA = UUID.randomUUID();
        var picks = List.of(new QuestionPick(both.toString(), 1), new QuestionPick(onlyA.toString(), 1),
                new QuestionPick(anotherA.toString(), 1));
        var section = new SectionOnPath("MATH", "math", 3, null, List.of(GroupQuota.single("group", 3)));
        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("group", new GroupWitness("group", picks)),
                Map.of(new QuestionSourcePort.VersionKey(both, 1), candidate(both, "G12", List.of(a, b)),
                        new QuestionSourcePort.VersionKey(onlyA, 1), candidate(onlyA, "G12", List.of(a)),
                        new QuestionSourcePort.VersionKey(anotherA, 1), candidate(anotherA, "G12", List.of(a))),
                List.of(), List.of());
        assertThat(rows).contains(
                new Compliance("TOPIC:a", "SECTION:math", BigDecimal.ONE, BigDecimal.valueOf(3), "REQUIRED", "QUESTION_INDEPENDENT", null),
                new Compliance("TOPIC:b", "SECTION:math", BigDecimal.ONE, BigDecimal.ONE, "REQUIRED", "QUESTION_INDEPENDENT", null));
    }

    @Test
    void topicGradeFilterDoesNotMatchAnUngradedQuestion() {
        UUID node = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"math","subjectCode":"MATH","questionCount":1,
                  "topicQuotas":[{"id":"a","nodeIds":["%s"],"count":1,"gradeCodes":["G12"]}],
                  "blocks":[{"id":"group","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """.formatted(node));
        var section = new SectionOnPath("MATH", "math", 1, null, List.of(GroupQuota.single("group", 1)));
        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("group", new GroupWitness("group", List.of(new QuestionPick(question.toString(), 1)))),
                Map.of(new QuestionSourcePort.VersionKey(question, 1), candidate(question, null, List.of(node))),
                List.of(), List.of());
        assertThat(rows).contains(new Compliance("TOPIC:a", "SECTION:math", BigDecimal.ONE, BigDecimal.ZERO,
                "REQUIRED", "QUESTION_INDEPENDENT", null));
    }

    @Test
    void examPercentagesUseLargestRemainderAcrossBuckets() {
        var document = codec.read("""
                {"parts":[{"id":"part","sections":[{"id":"math","subjectCode":"MATH","questionCount":2,
                  "blocks":[{"id":"group","questionType":"SINGLE_CHOICE","questionCount":2}]}]}],
                 "examQuotas":[{"id":"grades","dimension":"GRADE","basis":"QUESTION","enforcement":"REFERENCE",
                   "buckets":[{"codes":["G10"],"percent":34},{"codes":["G11"],"percent":33},{"codes":["G12"],"percent":33}]}]}
                """);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var picks = List.of(new QuestionPick(first.toString(), 1), new QuestionPick(second.toString(), 1));
        var section = new SectionOnPath("MATH", "math", 2, null, List.of(GroupQuota.single("group", 2)));
        List<Compliance> rows = PreviewCompliance.evaluate(document, SolvePath.of(0, List.of(section)),
                Map.of("group", new GroupWitness("group", picks)),
                Map.of(new QuestionSourcePort.VersionKey(first, 1), candidate(first, "G10", List.of()),
                        new QuestionSourcePort.VersionKey(second, 1), candidate(second, "G11", List.of())),
                List.of(), List.of());
        assertThat(rows).contains(
                new Compliance("EXAM:grades:G10", "EXAM", BigDecimal.ONE, BigDecimal.ONE, "REFERENCE", "QUESTION", null),
                new Compliance("EXAM:grades:G11", "EXAM", BigDecimal.ONE, BigDecimal.ONE, "REFERENCE", "QUESTION", null),
                new Compliance("EXAM:grades:G12", "EXAM", BigDecimal.ZERO, BigDecimal.ZERO, "REFERENCE", "QUESTION", null));
    }

    private static QuestionSourcePort.Candidate candidate(UUID id, String grade, List<UUID> nodes) {
        var unit = new QuestionSourcePort.Unit(id, id.toString(), QuestionType.SINGLE_CHOICE, grade,
                "EASY", nodes, List.of(), BigDecimal.ONE);
        return new QuestionSourcePort.Candidate(id, 1, null, List.of(unit), null, null);
    }
}
