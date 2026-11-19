package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamQuota;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MatrixMode;
import vn.unicom.thi360.exam.domain.MatrixUnit;
import vn.unicom.thi360.exam.domain.QuotaBasis;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.LargestRemainderApportioner;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/**
 * Reference (non-required) comparisons on the proof paper (2B 6.2, 7.3; K9): the solver only
 * enforces {@code REQUIRED} quotas, so {@code REFERENCE} matrices and {@code exam_quotas} are
 * compared here against the questions the solver actually picked. A deviating distribution yields
 * {@code DISTRIBUTION_DEVIATION}, a deviating exam-level quota yields {@code EXAM_QUOTA_DEVIATION}
 * (warnings; they never block publish). The same witness counts feed the Required/Actual rows of
 * each subject ({@code achieved}).
 */
final class ExamCheckDistribution {
    private static final LargestRemainderApportioner APPORTIONER = new LargestRemainderApportioner();

    /**
     * One question placed on the proof paper, attributed to its block and section. {@code quotaAnswerType} is the
     * subject answer-quota row it counts under (UNI-2754); null means its own answer type.
     */
    public record PickedUnit(String subjectCode, String sectionId, Integer sectionCount,
                             boolean fixed, String blockId, QuestionSourcePort.Unit unit, String quotaAnswerType) {
        public PickedUnit(String subjectCode, String sectionId, Integer sectionCount,
                          boolean fixed, String blockId, QuestionSourcePort.Unit unit) {
            this(subjectCode, sectionId, sectionCount, fixed, blockId, unit, null);
        }
    }

    /** Proved paper of one path plus the code labels it represents. */
    public record PathEvidence(PathResult path, String pathId, List<String> codes,
                               List<PickedUnit> units) {
        public PathEvidence {
            codes = codes == null ? List.of() : List.copyOf(codes);
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    /** Counts of one dimension on the proof paper, in basis units. */
    public record DimensionCounts(Map<String, BigDecimal> grade, Map<String, BigDecimal> difficulty,
                                  Map<List<String>, BigDecimal> cells, Map<String, BigDecimal> answerTypes,
                                  BigDecimal total) {
        public DimensionCounts {
            grade = grade == null ? Map.of() : Map.copyOf(grade);
            difficulty = difficulty == null ? Map.of() : Map.copyOf(difficulty);
            cells = cells == null ? Map.of() : Map.copyOf(cells);
            answerTypes = answerTypes == null ? Map.of() : Map.copyOf(answerTypes);
            total = total == null ? BigDecimal.ZERO : total;
        }
        public DimensionCounts() { this(Map.of(), Map.of(), Map.of(), Map.of(), BigDecimal.ZERO); }
    }

    /** Witness counts per dimension of a section scope, keyed by counting basis. */
    public record BasisCounts(Map<QuotaBasis, DimensionCounts> byBasis) {
        public BasisCounts {
            byBasis = byBasis == null ? Map.of() : Map.copyOf(byBasis);
        }
        public DimensionCounts basis(QuotaBasis basis) {
            return byBasis.getOrDefault(basis == null ? QuotaBasis.QUESTION : basis,
                    new DimensionCounts());
        }
        static BasisCounts empty() { return new BasisCounts(Map.of()); }
    }

    /** Deviations and witness counts of one path; {@code issues} carry the WARNING findings. */
    public record Report(List<ObjectNode> issues,
                         Map<String, Map<Integer, BasisCounts>> sectionActuals) {
        static Report empty() { return new Report(List.of(), Map.of()); }
    }

    /** Index of the document needed to resolve witness groups back to their declaration. */
    private final ExamStructureDocument document;
    private final ExamCheckIssues issues;
    private final Map<String, ExamSection> sections = new LinkedHashMap<>();
    private final Map<String, ExamPart> sectionParts = new LinkedHashMap<>();
    private final Map<String, String> sectionOptions = new LinkedHashMap<>();

    private ExamCheckDistribution(ObjectMapper mapper, ExamStructureDocument document) {
        this.document = document;
        this.issues = new ExamCheckIssues(mapper);
        for (ExamPart part : document.parts()) {
            for (ExamSection section : part.subjectPool()) index(part, null, section);
            for (ExamSection section : part.sections()) index(part, null, section);
            for (ExamPartOption option : part.options()) {
                for (ExamSection section : option.sections()) index(part, option.id(), section);
                if (!option.blocks().isEmpty()) {
                    int count = option.blocks().stream()
                            .mapToInt(b -> b.questionCount() != null ? b.questionCount()
                                    : (b.setCount() != null && b.setSize() != null
                                            ? b.setCount() * b.setSize() : 0))
                            .sum();
                    index(part, option.id(), new ExamSection(option.id() + ":blocks", option.code(),
                            count, null, null, null, null, option.blocks()));
                }
            }
        }
    }

    private void index(ExamPart part, String optionId, ExamSection section) {
        sections.put(section.id(), section);
        sectionParts.put(section.id(), part);
        sectionOptions.put(section.id(), optionId);
    }

    /**
     * Compare every REFERENCE distribution and exam quota against the proved picks.
     * {@code units} comes from {@link #pickedUnits}.
     */
    static Report analyze(ExamStructureDocument document, PathEvidence evidence, ObjectMapper mapper) {
        if (evidence == null || evidence.units().isEmpty()) return Report.empty();
        return new ExamCheckDistribution(mapper, document).compare(evidence);
    }

    /**
     * Flatten the proved picks of one {@link PathResult}: every pick of every {@link GroupWitness}
     * is attributed to the {@link SectionOnPath} that owns its group (with its resolved count in
     * the matching selection) and to the declaring block. Candidates are keyed
     * {@code "questionId:versionNo"} ({@link #candidateKey}).
     */
    static List<PickedUnit> pickedUnits(PathResult result,
                                        Map<String, QuestionSourcePort.Candidate> candidates) {
        List<PickedUnit> out = new ArrayList<>();
        Set<SubjectWitness> pending = new LinkedHashSet<>(result.witness());
        for (List<SubjectPick> tuple : tuples(result.path().picks())) {
            Map<String, SectionOnPath> byGroup = new LinkedHashMap<>();
            Map<String, Integer> countBySection = new LinkedHashMap<>();
            Map<String, Integer> totalBySubject = new LinkedHashMap<>();
            List<SectionOnPath> scope = new ArrayList<>(result.path().sections());
            for (SubjectPick pick : tuple) scope.addAll(pick.resolvedSections());
            for (SectionOnPath section : scope) {
                for (GroupQuota group : section.groups()) {
                    byGroup.putIfAbsent(group.groupId(), section);
                }
                countBySection.put(section.sectionId(),
                        section.questionCount() == null ? 0 : section.questionCount());
                totalBySubject.merge(section.subjectCode(),
                        section.questionCount() == null ? 0 : section.questionCount(), Integer::sum);
            }
            for (SubjectWitness witness : List.copyOf(pending)) {
                if (!Objects.equals(totalBySubject.get(witness.subjectCode()), witness.resolvedCount())) continue;
                List<SectionOnPath> own = scope.stream()
                        .filter(section -> section.subjectCode().equals(witness.subjectCode())).toList();
                if (own.isEmpty()) continue;
                Set<String> groupIds = new LinkedHashSet<>();
                witness.groups().forEach(group -> groupIds.add(group.groupId()));
                if (!byGroup.keySet().containsAll(groupIds)) continue;
                pending.remove(witness);
                for (GroupWitness group : witness.groups()) {
                    SectionOnPath section = byGroup.get(group.groupId());
                    if (section == null || !section.subjectCode().equals(witness.subjectCode())) continue;
                    GroupQuota quota = section.groups().stream()
                            .filter(g -> g.groupId().equals(group.groupId())).findFirst().orElse(null);
                    String blockId = quota != null && quota.slotParent() != null
                            ? quota.slotParent().parentGroupId() : group.groupId();
                    boolean fixed = result.path().sections().stream()
                            .anyMatch(s -> s.sectionId().equals(section.sectionId()));
                    for (QuestionPick pick : group.picks()) {
                        QuestionSourcePort.Candidate candidate =
                                candidates.get(candidateKey(pick.questionId(), pick.versionNo()));
                        if (candidate == null || candidate.units() == null) continue;
                        for (QuestionSourcePort.Unit unit : candidate.units()) {
                            out.add(new PickedUnit(section.subjectCode(), section.sectionId(),
                                    section.questionCount(), fixed, blockId, unit,
                                    unit.answerType() == null ? null
                                            : AnswerQuotaCounting.key(section, quota, unit.answerType())));
                        }
                    }
                }
            }
        }
        return out;
    }

    static String candidateKey(String questionId, int versionNo) {
        return questionId + ":" + versionNo;
    }

    private Report compare(PathEvidence evidence) {
        List<ObjectNode> found = new ArrayList<>();
        Map<String, Map<Integer, BasisCounts>> sectionActuals = new LinkedHashMap<>();
        Map<String, Map<Integer, List<PickedUnit>>> bySectionCount = new LinkedHashMap<>();
        for (PickedUnit unit : evidence.units()) {
            bySectionCount.computeIfAbsent(unit.sectionId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(unit.sectionCount(), ignored -> new ArrayList<>()).add(unit);
        }
        bySectionCount.forEach((sectionId, variants) -> {
            Map<Integer, BasisCounts> counts = new LinkedHashMap<>();
            variants.forEach((count, units) -> counts.put(count, counts(units)));
            sectionActuals.put(sectionId, counts);
        });
        // Section and block reference distributions, once per (section, resolved count).
        bySectionCount.forEach((sectionId, variants) -> {
            ExamSection section = sections.get(sectionId);
            if (section == null) return;
            variants.forEach((count, units) -> {
                compareMatrices(found, section.matrix(), section, null, evidence, units);
                for (QuestionBlock block : section.blocks()) {
                    if (Boolean.TRUE.equals(block.omitted()) || block.matrix() == null) continue;
                    List<PickedUnit> blockUnits = units.stream()
                            .filter(unit -> block.id().equals(unit.blockId())).toList();
                    compareMatrices(found, block.matrix(), section, block, evidence, blockUnits);
                }
            });
        });
        // Exam-level reference quotas, once per selection tuple.
        for (List<SubjectPick> tuple : tuples(evidence.path().path().picks())) {
            Map<String, Integer> tupleCounts = new LinkedHashMap<>();
            for (SubjectPick pick : tuple) {
                for (SectionOnPath section : pick.resolvedSections()) {
                    tupleCounts.put(section.sectionId(), section.questionCount());
                }
            }
            List<PickedUnit> scope = evidence.units().stream()
                    .filter(unit -> unit.fixed() || tupleCounts.containsKey(unit.sectionId())
                            && Objects.equals(tupleCounts.get(unit.sectionId()), unit.sectionCount()))
                    .toList();
            String signature = signature(tuple);
            for (ExamQuota quota : document.examQuotas()) {
                if (quota.enforcement() == QuotaEnforcement.REQUIRED) continue;
                compareQuota(found, quota, scope, evidence, signature);
            }
        }
        return new Report(found, sectionActuals);
    }

    private void compareMatrices(List<ObjectNode> found, BlockMatrix matrix, ExamSection section,
                                 QuestionBlock block, PathEvidence evidence, List<PickedUnit> units) {
        if (matrix == null || matrix.mode() == null) return;
        QuotaBasis basis = matrix.basis() == null ? QuotaBasis.QUESTION : matrix.basis();
        DimensionCounts actual = counts(units).basis(basis);
        BigDecimal total = actual.total();
        if (matrix.mode() == MatrixMode.COMBINED) {
            if (matrix.gradeEnforcement() == QuotaEnforcement.REFERENCE
                    || matrix.difficultyEnforcement() == QuotaEnforcement.REFERENCE) {
                List<BigDecimal> values = matrix.cells().stream().map(c -> c.value()).toList();
                BigDecimal[] required = resolve(values, matrix.unit(), total, basis);
                if (required == null) return;
                for (int i = 0; i < required.length; i++) {
                    var cell = matrix.cells().get(i);
                    BigDecimal have = actual.cells().getOrDefault(
                            List.of(String.valueOf(cell.gradeCode()), String.valueOf(cell.difficultyCode())), BigDecimal.ZERO);
                    if (have.compareTo(required[i]) != 0) {
                        found.add(deviation(section, block, evidence, "GRADE",
                                cell.gradeCode(), cell.difficultyCode(), required[i], have, basis));
                    }
                }
            }
            return;
        }
        if (matrix.gradeEnforcement() == QuotaEnforcement.REFERENCE) {
            compareDimension(found, section, block, evidence, "GRADE",
                    matrix.byGrade().stream().map(v -> v.value()).toList(),
                    matrix.byGrade().stream().map(v -> v.code()).toList(), matrix.unit(),
                    actual.grade(), total, basis);
        }
        if (matrix.difficultyEnforcement() == QuotaEnforcement.REFERENCE) {
            compareDimension(found, section, block, evidence, "DIFFICULTY",
                    matrix.byDifficulty().stream().map(v -> v.value()).toList(),
                    matrix.byDifficulty().stream().map(v -> v.code()).toList(), matrix.unit(),
                    actual.difficulty(), total, basis);
        }
    }

    private void compareDimension(List<ObjectNode> found, ExamSection section, QuestionBlock block,
                                  PathEvidence evidence, String dimension, List<BigDecimal> values,
                                  List<String> codes, MatrixUnit unit, Map<String, BigDecimal> actual,
                                  BigDecimal total, QuotaBasis basis) {
        if (values.isEmpty()) return;
        BigDecimal[] required = resolve(values, unit, total, basis);
        if (required == null) return;
        for (int i = 0; i < required.length; i++) {
            BigDecimal have = actual.getOrDefault(codes.get(i), BigDecimal.ZERO);
            if (have.compareTo(required[i]) != 0) {
                found.add(deviation(section, block, evidence, dimension, codes.get(i), null,
                        required[i], have, basis));
            }
        }
    }

    private void compareQuota(List<ObjectNode> found, ExamQuota quota, List<PickedUnit> scope,
                              PathEvidence evidence, String signature) {
        List<PickedUnit> applicable = scope.stream()
                .filter(unit -> quota.subjectCodes().isEmpty()
                        || quota.subjectCodes().contains(unit.subjectCode()))
                .filter(unit -> !quota.excludeSubjectCodes().contains(unit.subjectCode()))
                .toList();
        if (applicable.isEmpty()) return;
        QuotaBasis basis = quota.basis() == null ? QuotaBasis.QUESTION : quota.basis();
        DimensionCounts actual = counts(applicable).basis(basis);
        BigDecimal total = actual.total();
        if (quota.buckets().isEmpty()) return;
        BigDecimal[] required = new BigDecimal[quota.buckets().size()];
        boolean allPercent = quota.buckets().stream().allMatch(bucket -> bucket.count() == null && bucket.percent() != null);
        if (allPercent && basis != QuotaBasis.POINTS) {
            List<BigDecimal> percents = quota.buckets().stream().map(ExamQuota.Bucket::percent).toList();
            required = resolve(percents, MatrixUnit.PERCENT, total, basis);
            if (required == null) return;
        } else {
            for (int i = 0; i < required.length; i++) {
                ExamQuota.Bucket bucket = quota.buckets().get(i);
                if (bucket.count() != null) { required[i] = BigDecimal.valueOf(bucket.count()); continue; }
                if (bucket.percent() == null) return;
                BigDecimal amount = bucket.percent().multiply(total).movePointLeft(2);
                required[i] = basis == QuotaBasis.POINTS ? amount : amount.setScale(0, RoundingMode.HALF_UP);
            }
        }
        for (int i = 0; i < required.length; i++) {
            List<String> codes = quota.buckets().get(i).codes();
            BigDecimal have = applicable.stream()
                    .filter(unit -> matches(unit.unit(), quota.dimension(), codes))
                    .map(unit -> weight(unit.unit(), basis)).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (have.compareTo(required[i]) == 0) continue;
            ObjectNode issue = issues.create("EXAM_QUOTA_DEVIATION", "MATRIX", "WARNING");
            issue.put("dimension", quota.dimension() == null ? null : quota.dimension().name());
            if (quota.dimension() == ExamQuota.Dimension.DIFFICULTY) {
                issue.put("difficultyCode", String.join("+", codes));
            } else {
                issue.put("gradeCode", String.join("+", codes));
            }
            issue.put("required", required[i]);
            issue.put("available", have);
            issues.key(issue);
            issue.put("key", issue.path("key").asText() + "|" + quota.id() + signature);
            if (evidence.pathId() != null) issue.putObject("location").put("pathId", evidence.pathId());
            ArrayNode affected = issue.withArray("affectedCodes");
            evidence.codes().forEach(affected::add);
            found.add(issue);
        }
    }

    private static boolean matches(QuestionSourcePort.Unit unit, ExamQuota.Dimension dimension,
                                   List<String> codes) {
        String value = dimension == ExamQuota.Dimension.DIFFICULTY ? unit.difficultyCode() : unit.gradeCode();
        return value != null && codes.contains(value);
    }

    private ObjectNode deviation(ExamSection section, QuestionBlock block, PathEvidence evidence,
                                 String dimension, String gradeCode, String difficultyCode,
                                 BigDecimal required, BigDecimal available, QuotaBasis basis) {
        ObjectNode issue = issues.create("DISTRIBUTION_DEVIATION", "MATRIX", "WARNING");
        ExamPart part = sectionParts.get(section.id());
        String optionId = sectionOptions.get(section.id());
        if (part != null) { issue.put("partId", part.id()); issue.put("partName", part.name()); }
        issue.put("optionId", optionId);
        issue.put("sectionId", section.id());
        issue.put("subjectCode", section.subjectCode());
        if (block != null) { issue.put("blockId", block.id()); issue.put("blockName", block.name()); }
        issue.put("dimension", dimension);
        if (gradeCode != null) issue.put("gradeCode", gradeCode);
        if (difficultyCode != null) issue.put("difficultyCode", difficultyCode);
        issue.put("required", required);
        issue.put("available", available);
        issues.key(issue);
        String suffix = "|" + basis + (evidence.pathId() == null ? "" : "|" + evidence.pathId());
        issue.put("key", issue.path("key").asText() + suffix);
        return issue;
    }

    private static BasisCounts counts(List<PickedUnit> units) {
        Map<QuotaBasis, DimensionCounts> out = new LinkedHashMap<>();
        for (QuotaBasis basis : QuotaBasis.values()) {
            Map<String, BigDecimal> grade = new LinkedHashMap<>();
            Map<String, BigDecimal> difficulty = new LinkedHashMap<>();
            Map<List<String>, BigDecimal> cells = new LinkedHashMap<>();
            Map<String, BigDecimal> answers = new LinkedHashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (PickedUnit unit : units) {
                QuestionSourcePort.Unit u = unit.unit();
                BigDecimal weight = weight(u, basis);
                total = total.add(weight);
                if (u.gradeCode() != null) grade.merge(u.gradeCode(), weight, BigDecimal::add);
                if (u.difficultyCode() != null) difficulty.merge(u.difficultyCode(), weight, BigDecimal::add);
                if (u.gradeCode() != null && u.difficultyCode() != null) {
                    cells.merge(List.of(u.gradeCode(), u.difficultyCode()), weight, BigDecimal::add);
                }
                if (u.answerType() != null) {
                    answers.merge(unit.quotaAnswerType() == null ? u.answerType().name() : unit.quotaAnswerType(),
                            weight, BigDecimal::add);
                }
            }
            out.put(basis, new DimensionCounts(grade, difficulty, cells, answers, total));
        }
        return new BasisCounts(out);
    }

    /** Unit of the comparison (2B 6.2, R-87): 1 unit per question, statements / left items / points. */
    private static BigDecimal weight(QuestionSourcePort.Unit unit, QuotaBasis basis) {
        return switch (basis) {
            case PROMPT -> BigDecimal.valueOf(unit.commandCount());
            case POINTS -> unit.points() == null ? BigDecimal.ZERO : unit.points();
            case QUESTION -> BigDecimal.ONE;
        };
    }

    /** Resolve declared matrix values (COUNT or PERCENT) against the basis total on the paper. */
    static BigDecimal[] resolve(List<BigDecimal> values, MatrixUnit unit, BigDecimal total, QuotaBasis basis) {
        if (unit == null || values.stream().anyMatch(Objects::isNull)) return null;
        if (unit != MatrixUnit.PERCENT) return values.toArray(BigDecimal[]::new);
        if (basis == QuotaBasis.POINTS) {
            return values.stream().map(value -> value.multiply(total).movePointLeft(2)).toArray(BigDecimal[]::new);
        }
        List<Integer> percents = new ArrayList<>(values.size());
        for (BigDecimal value : values) {
            try { percents.add(value.intValueExact()); }
            catch (ArithmeticException e) { return null; }
        }
        int[] counts = APPORTIONER.apportion(percents, total.intValueExact());
        return java.util.Arrays.stream(counts).mapToObj(BigDecimal::valueOf).toArray(BigDecimal[]::new);
    }

    private static List<List<SubjectPick>> tuples(List<List<SubjectPick>> picks) {
        if (picks == null || picks.isEmpty()) return List.of(List.of());
        List<List<SubjectPick>> out = new ArrayList<>();
        product(picks, 0, new ArrayList<>(), out);
        return out;
    }

    private static void product(List<List<SubjectPick>> choices, int index, List<SubjectPick> prefix,
                                List<List<SubjectPick>> out) {
        if (index == choices.size()) { out.add(List.copyOf(prefix)); return; }
        for (SubjectPick pick : choices.get(index)) {
            prefix.add(pick);
            product(choices, index + 1, prefix, out);
            prefix.removeLast();
        }
    }

    private static String signature(List<SubjectPick> tuple) {
        StringBuilder out = new StringBuilder();
        for (SubjectPick pick : tuple) {
            out.append('|').append(pick.optionId()).append(':');
            for (int i = 0; i < pick.sections().size(); i++) {
                out.append(pick.sections().get(i).sectionId()).append('=').append(pick.counts().get(i));
            }
        }
        return out.toString();
    }

    static String pathId(PathResult result) {
        var chosen = result.path().chosen();
        return chosen.isEmpty() ? "default"
                : String.join("+", chosen.stream().map(choice -> choice.optionId()).toList());
    }
}
