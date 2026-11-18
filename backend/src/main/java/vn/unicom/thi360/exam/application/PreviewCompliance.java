package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.exam.api.ExamPreviewController.Compliance;
import vn.unicom.thi360.exam.domain.*;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.LargestRemainderApportioner;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Reports configured quotas against the retained counting units of one generated paper. */
final class PreviewCompliance {
    private record Datum(String sectionId, String subjectCode, String blockId,
                         QuestionSourcePort.Unit unit, boolean dropped) {}

    static List<Compliance> evaluate(ExamStructureDocument document, SolvePath path,
            Map<String, GroupWitness> witnesses,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates,
            List<PreviewSnapshotStore.Dropped> dropped, List<CatalogItemView> knowledge) {
        Map<String, ExamSection> sections = new HashMap<>();
        Map<String, QuestionBlock> blocks = new HashMap<>();
        for (ExamPart part : document.parts()) {
            index(part.sections(), sections, blocks);
            index(part.subjectPool(), sections, blocks);
            for (ExamPartOption option : part.options()) {
                index(option.sections(), sections, blocks);
                for (QuestionBlock block : option.blocks()) blocks.put(block.id(), block);
            }
        }
        List<SectionOnPath> selected = new ArrayList<>(path.sections());
        for (List<SubjectPick> picks : path.picks()) for (SubjectPick pick : picks) selected.addAll(pick.resolvedSections());
        List<Datum> data = new ArrayList<>();
        Map<String, List<Datum>> perSection = new LinkedHashMap<>();
        Map<String, List<Datum>> perBlock = new LinkedHashMap<>();
        for (SectionOnPath section : selected) {
            for (var group : section.groups()) {
                GroupWitness witness = witnesses.get(group.groupId());
                if (witness == null) continue;
                String blockId = blockId(group.groupId(), blocks);
                for (QuestionPick pick : witness.picks()) {
                    var candidate = candidates.get(key(pick));
                    if (candidate == null) throw new IllegalStateException("Selected candidate missing");
                    for (var unit : candidate.units()) add(data, perSection, perBlock,
                            new Datum(section.sectionId(), section.subjectCode(), blockId, unit, false));
                }
            }
        }
        for (var item : dropped) {
            var candidate = candidates.get(key(item.pick()));
            if (candidate == null || candidate.units().size() != 1) throw new IllegalStateException("Dropped candidate missing");
            SectionOnPath section = selected.stream().filter(s -> s.subjectCode().equals(item.subjectCode()))
                    .findFirst().orElseThrow();
            String block = droppedBlock(section, witnesses, candidates, blocks);
            add(data, perSection, perBlock,
                    new Datum(section.sectionId(), section.subjectCode(), block, candidate.units().getFirst(), true));
        }
        List<Compliance> result = new ArrayList<>();
        Map<java.util.UUID, CatalogItemView> knowledgeById = new HashMap<>();
        for (CatalogItemView item : knowledge) knowledgeById.put(item.id(), item);
        for (SectionOnPath selectedSection : selected) {
            ExamSection section = sections.get(selectedSection.sectionId());
            if (section == null) continue;
            List<Datum> units = perSection.getOrDefault(section.id(), List.of());
            if (section.answerQuotas() != null) {
                var quotas = section.answerQuotas();
                List<BigDecimal> required = requirements(quotas.items().stream().map(AnswerQuota::value).toList(),
                        quotas.unit(), QuotaBasis.QUESTION, units);
                for (int i = 0; i < quotas.items().size(); i++) {
                    String type = quotas.items().get(i).answerType().name();
                    addRow(result, "ANSWER_TYPE:" + type, "SECTION:" + section.id(), required.get(i),
                            QuotaEnforcement.REQUIRED, QuotaBasis.QUESTION, units,
                            datum -> type.equals(datum.unit().answerType().name()));
                }
            }
            matrix(result, "SECTION:" + section.id(), section.matrix(), units);
            topics(result, "SECTION:" + section.id(), section.topicQuotas(), units, knowledgeById);
            for (QuestionBlock block : section.blocks()) {
                if (!Boolean.TRUE.equals(block.omitted())) {
                    matrix(result, "BLOCK:" + block.id(), block.matrix(), perBlock.getOrDefault(block.id(), List.of()));
                    topics(result, "BLOCK:" + block.id(), block.topicQuotas(),
                            perBlock.getOrDefault(block.id(), List.of()), knowledgeById);
                }
            }
        }
        examQuotas(result, document.examQuotas(), data);
        return List.copyOf(result);
    }

    private static void index(List<ExamSection> values, Map<String, ExamSection> sections,
            Map<String, QuestionBlock> blocks) {
        for (ExamSection section : values) {
            sections.put(section.id(), section);
            for (QuestionBlock block : section.blocks()) blocks.put(block.id(), block);
        }
    }

    private static void add(List<Datum> all, Map<String, List<Datum>> sections,
            Map<String, List<Datum>> blocks, Datum datum) {
        all.add(datum);
        sections.computeIfAbsent(datum.sectionId(), ignored -> new ArrayList<>()).add(datum);
        blocks.computeIfAbsent(datum.blockId(), ignored -> new ArrayList<>()).add(datum);
    }

    private static String blockId(String groupId, Map<String, QuestionBlock> blocks) {
        if (blocks.containsKey(groupId)) return groupId;
        int slot = groupId.lastIndexOf(":slot:");
        return slot < 0 ? groupId : groupId.substring(0, slot);
    }

    private static String droppedBlock(SectionOnPath section, Map<String, GroupWitness> witnesses,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates,
            Map<String, QuestionBlock> blocks) {
        for (var group : section.groups()) {
            if (group.questionCount() == null) continue;
            GroupWitness witness = witnesses.get(group.groupId());
            int actual = 0;
            if (witness != null) for (QuestionPick pick : witness.picks()) {
                var candidate = candidates.get(key(pick));
                if (candidate != null) actual += candidate.units().size();
            }
            if (actual < group.questionCount()) return blockId(group.groupId(), blocks);
        }
        return section.groups().stream().filter(group -> group.kind() == vn.unicom.thi360.exam.domain.solver.GroupQuota.GroupKind.SINGLE
                        && group.questionCount() == null)
                .map(group -> blockId(group.groupId(), blocks)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Dropped question group missing"));
    }

    private static void matrix(List<Compliance> result, String scope, BlockMatrix matrix, List<Datum> data) {
        if (matrix == null) return;
        QuotaBasis basis = matrix.basis();
        List<BigDecimal> cells = requirements(matrix.cells().stream().map(MatrixCell::value).toList(), matrix.unit(), basis, data);
        for (int i = 0; i < matrix.cells().size(); i++) {
            MatrixCell cell = matrix.cells().get(i);
            addRow(result, "CELL:" + cell.gradeCode() + ":" + cell.difficultyCode(), scope, cells.get(i),
                    matrix.gradeEnforcement(), basis, data, datum -> Objects.equals(cell.gradeCode(), datum.unit().gradeCode())
                            && Objects.equals(cell.difficultyCode(), datum.unit().difficultyCode()));
        }
        dimensions(result, scope, "GRADE", matrix.byGrade(), matrix.unit(), matrix.gradeEnforcement(), basis, data);
        dimensions(result, scope, "DIFFICULTY", matrix.byDifficulty(), matrix.unit(), matrix.difficultyEnforcement(), basis, data);
        dimensions(result, scope, "COMPETENCY", matrix.byCompetency(), matrix.unit(), QuotaEnforcement.REQUIRED, basis, data);
    }

    private static void dimensions(List<Compliance> result, String scope, String dimension,
            List<MatrixDimensionValue> values, MatrixUnit unit, QuotaEnforcement enforcement,
            QuotaBasis basis, List<Datum> data) {
        List<BigDecimal> required = requirements(values.stream().map(MatrixDimensionValue::value).toList(), unit, basis, data);
        for (int i = 0; i < values.size(); i++) {
            String code = values.get(i).code();
            Predicate<Datum> matches = datum -> switch (dimension) {
                case "GRADE" -> Objects.equals(code, datum.unit().gradeCode());
                case "DIFFICULTY" -> Objects.equals(code, datum.unit().difficultyCode());
                default -> datum.unit().competencyCodes() != null && datum.unit().competencyCodes().contains(code);
            };
            addRow(result, dimension + ":" + code, scope, required.get(i), enforcement, basis, data, matches);
        }
    }

    private static void topics(List<Compliance> result, String scope, List<TopicQuota> quotas,
            List<Datum> data, Map<java.util.UUID, CatalogItemView> knowledge) {
        if (quotas.isEmpty()) return;
        int[] assigned = new int[data.size()];
        java.util.Arrays.fill(assigned, -1);
        int[] counts = new int[quotas.size()];
        boolean[] visited = new boolean[data.size()];
        for (int i = 0; i < data.size(); i++) {
            java.util.Arrays.fill(visited, false);
            assignTopic(i, data, quotas, knowledge, assigned, counts, visited);
        }
        for (int q = 0; q < quotas.size(); q++) {
            TopicQuota quota = quotas.get(q);
            QuotaBasis basis = quota.basis() == null ? QuotaBasis.QUESTION : quota.basis();
            QuotaEnforcement enforcement = quota.enforcement() == null ? QuotaEnforcement.REQUIRED : quota.enforcement();
            BigDecimal actual = BigDecimal.ZERO;
            BigDecimal removed = BigDecimal.ZERO;
            for (int i = 0; i < data.size(); i++) {
                if (assigned[i] != q) continue;
                BigDecimal amount = weight(data.get(i), basis);
                if (data.get(i).dropped()) removed = removed.add(amount);
                else actual = actual.add(amount);
            }
            result.add(new Compliance("TOPIC:" + quota.id(), scope,
                    BigDecimal.valueOf(quota.count()).subtract(removed), actual, enforcement.name(), basis.name(),
                    data.stream().anyMatch(Datum::dropped) ? "dropped 1" : null));
        }
    }

    private static boolean assignTopic(int unit, List<Datum> data, List<TopicQuota> quotas,
            Map<java.util.UUID, CatalogItemView> knowledge, int[] assigned, int[] counts, boolean[] visited) {
        if (visited[unit]) return false;
        visited[unit] = true;
        for (int q = 0; q < quotas.size(); q++) {
            TopicQuota quota = quotas.get(q);
            if (!matchesTopic(data.get(unit), quota, knowledge)) continue;
            int capacity = Math.max(0, quota.count());
            if (counts[q] < capacity) {
                assigned[unit] = q;
                counts[q]++;
                return true;
            }
            for (int other = 0; other < data.size(); other++) {
                if (assigned[other] == q && assignTopic(other, data, quotas, knowledge, assigned, counts, visited)) {
                    assigned[unit] = q;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesTopic(Datum datum, TopicQuota quota,
            Map<java.util.UUID, CatalogItemView> knowledge) {
        if ((!quota.gradeCodes().isEmpty() && !quota.gradeCodes().contains(datum.unit().gradeCode()))
                || (!quota.difficultyCodes().isEmpty() && !quota.difficultyCodes().contains(datum.unit().difficultyCode()))
                || (!quota.groupIds().isEmpty() && !quota.groupIds().contains(datum.blockId()))) return false;
        if (datum.unit().mapidIds() == null) return false;
        for (var node : datum.unit().mapidIds()) {
            for (var root : quota.nodeIds()) {
                if (root.equals(node)) return true;
                CatalogItemView parent = knowledge.get(root);
                CatalogItemView child = knowledge.get(node);
                if (parent != null && child != null && parent.path() != null && child.path() != null
                        && child.path().startsWith(parent.path())) return true;
            }
        }
        return false;
    }

    private static void examQuotas(List<Compliance> result, List<ExamQuota> quotas, List<Datum> all) {
        for (ExamQuota quota : quotas) {
            List<Datum> data = all.stream().filter(datum ->
                    (quota.subjectCodes().isEmpty() || quota.subjectCodes().contains(datum.subjectCode()))
                    && !quota.excludeSubjectCodes().contains(datum.subjectCode())).toList();
            QuotaBasis basis = quota.basis() == null ? QuotaBasis.QUESTION : quota.basis();
            for (ExamQuota.Bucket bucket : quota.buckets()) {
                BigDecimal required = bucket.count() != null ? BigDecimal.valueOf(bucket.count())
                        : sum(data, basis, ignored -> true).multiply(bucket.percent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                addRow(result, "EXAM:" + quota.id() + ":" + String.join(",", bucket.codes()), "EXAM",
                        required, quota.enforcement() == null ? QuotaEnforcement.REQUIRED : quota.enforcement(), basis, data,
                        datum -> bucket.codes().contains(quota.dimension() == ExamQuota.Dimension.GRADE
                                ? datum.unit().gradeCode() : datum.unit().difficultyCode()));
            }
        }
    }

    private static List<BigDecimal> requirements(List<BigDecimal> values, MatrixUnit unit, QuotaBasis basis, List<Datum> data) {
        if (values.isEmpty() || unit != MatrixUnit.PERCENT) return values;
        BigDecimal total = sum(data, basis, ignored -> true);
        if (basis == QuotaBasis.POINTS) return values.stream().map(value -> total.multiply(value)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)).toList();
        int[] counts = new LargestRemainderApportioner().apportion(values.stream().map(BigDecimal::intValueExact).toList(), total.intValueExact());
        List<BigDecimal> result = new ArrayList<>(counts.length);
        for (int count : counts) result.add(BigDecimal.valueOf(count));
        return result;
    }

    private static void addRow(List<Compliance> result, String criterion, String scope, BigDecimal required,
            QuotaEnforcement enforcement, QuotaBasis basis, List<Datum> data, Predicate<Datum> matches) {
        BigDecimal removed = sum(data, basis, datum -> datum.dropped() && matches.test(datum));
        BigDecimal actual = sum(data, basis, datum -> !datum.dropped() && matches.test(datum));
        result.add(new Compliance(criterion, scope, required.subtract(removed), actual,
                enforcement.name(), basis.name(), data.stream().anyMatch(Datum::dropped) ? "dropped 1" : null));
    }

    private static BigDecimal sum(List<Datum> data, QuotaBasis basis, Predicate<Datum> filter) {
        BigDecimal total = BigDecimal.ZERO;
        for (Datum datum : data) {
            if (!filter.test(datum)) continue;
            total = total.add(weight(datum, basis));
        }
        return total;
    }

    private static BigDecimal weight(Datum datum, QuotaBasis basis) {
        return switch (basis) {
            case QUESTION -> BigDecimal.ONE;
            case PROMPT -> BigDecimal.valueOf(datum.unit().commandCount());
            case POINTS -> datum.unit().points() == null ? BigDecimal.ZERO : datum.unit().points();
        };
    }

    private static QuestionSourcePort.VersionKey key(QuestionPick pick) {
        return new QuestionSourcePort.VersionKey(java.util.UUID.fromString(pick.questionId()), pick.versionNo());
    }
}
