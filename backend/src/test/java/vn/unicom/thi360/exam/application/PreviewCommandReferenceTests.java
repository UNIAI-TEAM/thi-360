package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamPreviewController.Compliance;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** BE-20 row 10: V-SAT Toán FULL compared by command (H44): denominator 67, unlabeled questions in their own row. */
class PreviewCommandReferenceTests {
    private static final String GROUPS = """
            "blocks":[
              {"id":"tf","questionType":"TRUE_FALSE","questionCount":9},
              {"id":"single","questionType":"SINGLE_CHOICE","questionCount":6},
              {"id":"match","questionType":"MATCHING","questionCount":5},
              {"id":"short","questionType":"SHORT_ANSWER","questionCount":5}]""";

    private final ExamStructureCodec codec = new ExamStructureCodec(new ObjectMapper());

    /** 9 Đúng/Sai (4 commands, grade 11) + 6 chọn một (grade 12) + 5 ghép (4 commands, grade 12) + 5 ngắn (no grade). */
    private final Map<String, List<QuestionSourcePort.Candidate>> byBlock = new LinkedHashMap<>();

    private String document(String basis) {
        return """
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"TOAN","questionCount":25,
                  "matrix":{"mode":"SEPARATE","unit":"COUNT","basis":"%s","gradeEnforcement":"REFERENCE",
                    "difficultyEnforcement":"REFERENCE",
                    "byGrade":[{"code":"G10","value":0},{"code":"G11","value":17},{"code":"G12","value":50}],
                    "byDifficulty":[{"code":"NB","value":13},{"code":"TH","value":35},{"code":"VD","value":19}]},
                  %s}]}]}
                """.formatted(basis, GROUPS);
    }

    private void paper() {
        add("tf", 9, QuestionType.TRUE_FALSE, 4, "G11", "TH");
        add("single", 6, QuestionType.SINGLE_CHOICE, 1, "G12", "NB");
        add("match", 5, QuestionType.MATCHING, 4, "G12", "VD");
        add("short", 5, QuestionType.SHORT_ANSWER, 1, null, null);
    }

    private void add(String block, int count, QuestionType type, int commands, String grade, String difficulty) {
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            var unit = new QuestionSourcePort.Unit(id, id.toString(), type, grade, difficulty, List.of(), List.of(),
                    BigDecimal.ONE, null, null, commands, null, null, null, null, false);
            byBlock.computeIfAbsent(block, ignored -> new ArrayList<>())
                    .add(new QuestionSourcePort.Candidate(id, 1, null, List.of(unit), null, null));
        }
    }

    private SectionOnPath section() {
        return new SectionOnPath("TOAN", "s", 25, null, List.of(GroupQuota.single("tf", 9),
                GroupQuota.single("single", 6), GroupQuota.single("match", 5), GroupQuota.single("short", 5)));
    }

    @Test
    void unlabeledCommandsGoToTheirOwnRowAndTheRowsAddUpToTheDenominator() {
        paper();
        Map<String, GroupWitness> witnesses = new LinkedHashMap<>();
        Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates = new LinkedHashMap<>();
        byBlock.forEach((block, list) -> {
            List<QuestionPick> picks = new ArrayList<>();
            for (var candidate : list) {
                picks.add(new QuestionPick(candidate.questionId().toString(), 1));
                candidates.put(new QuestionSourcePort.VersionKey(candidate.questionId(), 1), candidate);
            }
            witnesses.put(block, new GroupWitness(block, picks));
        });
        List<Compliance> rows = PreviewCompliance.evaluate(codec.read(document("PROMPT")),
                SolvePath.of(0, List.of(section())), witnesses, candidates, List.of(), List.of());
        assertThat(rows).contains(
                new Compliance("GRADE:G10", "SECTION:s", BigDecimal.ZERO, BigDecimal.ZERO, "REFERENCE", "PROMPT", null),
                new Compliance("GRADE:G11", "SECTION:s", BigDecimal.valueOf(17), BigDecimal.valueOf(36), "REFERENCE", "PROMPT", null),
                new Compliance("GRADE:G12", "SECTION:s", BigDecimal.valueOf(50), BigDecimal.valueOf(26), "REFERENCE", "PROMPT", null),
                new Compliance("GRADE:UNCLASSIFIED", "SECTION:s", null, BigDecimal.valueOf(5), "REFERENCE", "PROMPT", "UNCLASSIFIED"),
                new Compliance("DIFFICULTY:UNCLASSIFIED", "SECTION:s", null, BigDecimal.valueOf(5), "REFERENCE", "PROMPT", "UNCLASSIFIED"));
        BigDecimal denominator = rows.stream().filter(row -> row.criterion().startsWith("GRADE:"))
                .map(Compliance::actual).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(denominator).isEqualByComparingTo("67");
    }

    @Test
    void aMatrixCountedByQuestionKeepsItsRowsUnchanged() {
        paper();
        Map<String, GroupWitness> witnesses = new LinkedHashMap<>();
        Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates = new LinkedHashMap<>();
        byBlock.forEach((block, list) -> {
            List<QuestionPick> picks = new ArrayList<>();
            for (var candidate : list) {
                picks.add(new QuestionPick(candidate.questionId().toString(), 1));
                candidates.put(new QuestionSourcePort.VersionKey(candidate.questionId(), 1), candidate);
            }
            witnesses.put(block, new GroupWitness(block, picks));
        });
        List<Compliance> rows = PreviewCompliance.evaluate(codec.read(document("QUESTION")),
                SolvePath.of(0, List.of(section())), witnesses, candidates, List.of(), List.of());
        assertThat(rows).noneMatch(row -> row.criterion().endsWith("UNCLASSIFIED"));
    }

    @Test
    void theCheckComparesClassifiedCommandsAgainstTheTemplateAndIgnoresUnlabeledOnes() {
        paper();
        List<ExamCheckDistribution.PickedUnit> units = new ArrayList<>();
        byBlock.forEach((block, list) -> list.forEach(candidate -> units.add(new ExamCheckDistribution.PickedUnit(
                "TOAN", "s", 25, true, block, candidate.units().getFirst()))));
        var path = new PathResult(SolvePath.of(0, List.of(section())), SolveStatus.VALID, null, null, null,
                List.of(), List.of(), null);
        var report = ExamCheckDistribution.analyze(codec.read(document("PROMPT")),
                new ExamCheckDistribution.PathEvidence(path, "default", List.of(), units), new ObjectMapper());
        var byCode = new LinkedHashMap<String, BigDecimal[]>();
        report.issues().forEach(issue -> byCode.put(issue.path("gradeCode").asText(issue.path("difficultyCode").asText()),
                new BigDecimal[] {issue.path("required").decimalValue(), issue.path("available").decimalValue()}));
        assertThat(byCode.keySet()).containsExactlyInAnyOrder("G11", "G12", "NB", "TH", "VD");
        assertThat(byCode.get("G11")[0]).isEqualByComparingTo("17");
        assertThat(byCode.get("G11")[1]).isEqualByComparingTo("36");
        assertThat(byCode.get("G12")[1]).isEqualByComparingTo("26");
        assertThat(report.sectionActuals().get("s").get(25).basis(vn.unicom.thi360.exam.domain.QuotaBasis.PROMPT).total())
                .isEqualByComparingTo("67");
    }
}
