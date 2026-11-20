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
import vn.unicom.thi360.exam.domain.solver.TopicAssignment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Reports configured quotas against the retained counting units of one generated paper. */
final class PreviewCompliance {
    private record Datum(String sectionId, String subjectCode, String blockId,
                         QuestionSourcePort.Unit unit, boolean dropped,
                         List<TopicAssignment> assignments, int unitIndex) {}

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
                if (!option.blocks().isEmpty()) {
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), null,
                            null, null, null, null, option.blocks());
                    index(List.of(synthetic), sections, blocks);
                }
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
                    for (int i = 0; i < candidate.units().size(); i++) {
                        add(data, perSection, perBlock, new Datum(section.sectionId(), section.subjectCode(),
                                blockId, candidate.units().get(i), false, pick.topicAssignments(), i));
                    }
                }
            }
        }
        for (var item : dropped) {
            var candidate = candidates.get(key(item.pick()));
            if (candidate == null || candidate.units().size() != 1) throw new IllegalStateException("Dropped candidate missing");
            SectionOnPath section = selected.stream().filter(s -> s.sectionId().equals(item.sectionId()))
                    .findFirst().orElseThrow();
            String block = droppedBlock(section, witnesses, candidates, blocks);
            add(data, perSection, perBlock, new Datum(section.sectionId(), section.subjectCode(), block,
                    candidate.units().getFirst(), true, item.pick().topicAssignments(), 0));
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
                            datum -> type.equals(AnswerQuotaCounting.key(section, blocks.get(datum.blockId()),
                                    datum.unit().answerType())));
                }
            }
            matrix(result, "SECTION:" + section.id(), section.matrix(), units);
            // With a MAPID mode the rules, not the declared topic quotas, are the topic criteria (UNI-2758).
            boolean legacyTopics = section.mapidMode() == null;
            if (legacyTopics) topics(result, "SECTION:" + section.id(), section.topicQuotas(), units, knowledgeById);
            for (QuestionBlock block : section.blocks()) {
                if (!Boolean.TRUE.equals(block.omitted())) {
                    matrix(result, "BLOCK:" + block.id(), block.matrix(), perBlock.getOrDefault(block.id(), List.of()));
                    if (legacyTopics) topics(result, "BLOCK:" + block.id(), block.topicQuotas(),
                            perBlock.getOrDefault(block.id(), List.of()), knowledgeById);
                }
            }
            mapidRules(result, section, units, perBlock, knowledgeById);
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
                default -> code != null && datum.unit().competencyCodes() != null && datum.unit().competencyCodes().contains(code);
            };
            addRow(result, dimension + ":" + code, scope, required.get(i), enforcement, basis, data, matches);
        }
        if (!values.isEmpty() && !"COMPETENCY".equals(dimension)) {
            unclassified(result, scope, dimension, enforcement, basis, data);
        }
    }

    /**
     * H44: a PROMPT reference counts a command by the label of its question, so a question without the label is one
     * row apart (required null, note UNCLASSIFIED), not part of any bucket. Dropped questions are not counted.
     */
    private static void unclassified(List<Compliance> result, String scope, String dimension,
            QuotaEnforcement enforcement, QuotaBasis basis, List<Datum> data) {
        if (basis != QuotaBasis.PROMPT || enforcement != QuotaEnforcement.REFERENCE) return;
        Predicate<Datum> unlabeled = datum -> "GRADE".equals(dimension)
                ? datum.unit().gradeCode() == null : datum.unit().difficultyCode() == null;
        BigDecimal actual = sum(data, basis, datum -> !datum.dropped() && unlabeled.test(datum));
        if (actual.signum() > 0) {
            result.add(new Compliance(dimension + ":UNCLASSIFIED", scope, null, actual, enforcement.name(),
                    basis.name(), "UNCLASSIFIED"));
        }
    }

    private static void topics(List<Compliance> result, String scope, List<TopicQuota> quotas,
            List<Datum> data, Map<java.util.UUID, CatalogItemView> knowledge) {
        for (TopicQuota quota : quotas) {
            QuotaBasis basis = quota.basis() == null ? QuotaBasis.QUESTION : quota.basis();
            QuotaEnforcement enforcement = quota.enforcement() == null ? QuotaEnforcement.REQUIRED : quota.enforcement();
            boolean assigned = enforcement == QuotaEnforcement.REQUIRED;
            String[] scopeParts = scope.split(":", 2);
            String level = "BLOCK".equals(scopeParts[0]) ? "GROUP" : scopeParts[0];
            Predicate<Datum> matches = assigned
                    ? datum -> datum.assignments().stream().anyMatch(a -> a.unitIndex() == datum.unitIndex()
                            && level.equals(a.level()) && scopeParts[1].equals(a.scopeId())
                            && quota.id().equals(a.quotaId()))
                    : datum -> matchesTopic(datum, quota, knowledge);
            BigDecimal removed = sum(data, basis, datum -> datum.dropped() && matches.test(datum));
            BigDecimal actual = sum(data, basis, datum -> !datum.dropped() && matches.test(datum));
            result.add(new Compliance("TOPIC:" + quota.id(), scope,
                    BigDecimal.valueOf(quota.count()).subtract(removed), actual, enforcement.name(),
                    basis.name() + (assigned ? "" : "_INDEPENDENT"),
                    data.stream().anyMatch(Datum::dropped) ? "dropped 1" : null));
        }
    }

    private static boolean matchesTopic(Datum datum, TopicQuota quota,
            Map<java.util.UUID, CatalogItemView> knowledge) {
        if ((!quota.gradeCodes().isEmpty() && !contains(quota.gradeCodes(), datum.unit().gradeCode()))
                || (!quota.difficultyCodes().isEmpty() && !contains(quota.difficultyCodes(), datum.unit().difficultyCode()))
                || (!quota.groupIds().isEmpty() && !contains(quota.groupIds(), datum.blockId()))) return false;
        return carries(datum, quota.nodeIds(), knowledge);
    }

    /** The unit carries one of the nodes or a descendant of one (D23), as every MAPID rule counts it. */
    private static boolean carries(Datum datum, List<java.util.UUID> nodes,
            Map<java.util.UUID, CatalogItemView> knowledge) {
        if (datum.unit().mapidIds() == null) return false;
        for (var node : datum.unit().mapidIds()) {
            for (var root : nodes) {
                if (root.equals(node)) return true;
                CatalogItemView parent = knowledge.get(root);
                CatalogItemView child = knowledge.get(node);
                if (parent != null && child != null && parent.path() != null && child.path() != null
                        && child.path().startsWith(parent.path())) return true;
            }
        }
        return false;
    }

    /**
     * One row per PRESENT, COUNT and ONLY_IN_GROUP rule of a section in MATRIX mode (UNI-2758): {@code required}
     * is what the compiler made of the rule, {@code actual} the units of its scope that carry its nodes (a set
     * counts per child). PRESENT and COUNT report the subject ({@code SECTION:}) or the group ({@code BLOCK:});
     * ONLY_IN_GROUP reports what carries the node outside its group, required 0, and a rule with a number adds
     * {@code MAPID:<id>:COUNT} on its group. SOURCE rules are filters, not counts, and have no row.
     */
    private static void mapidRules(List<Compliance> result, ExamSection declared, List<Datum> units,
            Map<String, List<Datum>> perBlock, Map<java.util.UUID, CatalogItemView> knowledge) {
        if (declared.mapidMode() != MapidMode.MATRIX) return;
        CompiledMapidRules compiled = MapidRuleCompiler.compile(declared);
        Map<String, Integer> minimums = new HashMap<>();
        compiled.effectiveSection().topicQuotas().forEach(quota -> minimums.put(quota.id(), quota.count()));
        compiled.effectiveSection().blocks().forEach(block ->
                block.topicQuotas().forEach(quota -> minimums.put(quota.id(), quota.count())));
        Map<String, Integer> exacts = new HashMap<>();
        compiled.caps().forEach(cap -> exacts.put(cap.ruleId(), cap.max()));
        for (MapidRule rule : declared.mapidRules()) {
            if (rule.type() == MapidRuleType.SOURCE) continue;
            if (rule.groupId() != null && declared.blocks().stream().noneMatch(block -> rule.groupId().equals(block.id()))) continue;
            BigDecimal number = BigDecimal.valueOf(exacts.containsKey(rule.id())
                    ? exacts.get(rule.id()) : minimums.getOrDefault(rule.id(), 0));
            Predicate<Datum> carries = datum -> carries(datum, rule.nodeIds(), knowledge);
            String sectionScope = "SECTION:" + declared.id();
            if (rule.type() == MapidRuleType.ONLY_IN_GROUP) {
                addRow(result, "MAPID:" + rule.id(), sectionScope, BigDecimal.ZERO, QuotaEnforcement.REQUIRED,
                        QuotaBasis.QUESTION, units, datum -> !Objects.equals(rule.groupId(), datum.blockId())
                                && carries.test(datum));
                if (rule.count() != null || rule.percent() != null) {
                    addRow(result, "MAPID:" + rule.id() + ":COUNT", "BLOCK:" + rule.groupId(), number,
                            QuotaEnforcement.REQUIRED, QuotaBasis.QUESTION,
                            perBlock.getOrDefault(rule.groupId(), List.of()), carries);
                }
                continue;
            }
            BigDecimal required = rule.type() == MapidRuleType.PRESENT ? BigDecimal.ONE : number;
            boolean subject = rule.groupId() == null;
            addRow(result, "MAPID:" + rule.id(), subject ? sectionScope : "BLOCK:" + rule.groupId(), required,
                    QuotaEnforcement.REQUIRED, QuotaBasis.QUESTION,
                    subject ? units : perBlock.getOrDefault(rule.groupId(), List.of()), carries);
        }
    }

    private static void examQuotas(List<Compliance> result, List<ExamQuota> quotas, List<Datum> all) {
        for (ExamQuota quota : quotas) {
            List<Datum> data = all.stream().filter(datum ->
                    (quota.subjectCodes().isEmpty() || quota.subjectCodes().contains(datum.subjectCode()))
                    && !quota.excludeSubjectCodes().contains(datum.subjectCode())).toList();
            QuotaBasis basis = quota.basis() == null ? QuotaBasis.QUESTION : quota.basis();
            List<BigDecimal> percentages = quota.buckets().stream().filter(bucket -> bucket.count() == null)
                    .map(ExamQuota.Bucket::percent).toList();
            List<BigDecimal> apportioned = apportionPercentages(percentages, sum(data, basis, ignored -> true));
            int percentageIndex = 0;
            for (ExamQuota.Bucket bucket : quota.buckets()) {
                BigDecimal required = bucket.count() != null ? BigDecimal.valueOf(bucket.count())
                        : apportioned.get(percentageIndex++);
                addRow(result, "EXAM:" + quota.id() + ":" + String.join(",", bucket.codes()), "EXAM",
                        required, quota.enforcement() == null ? QuotaEnforcement.REQUIRED : quota.enforcement(), basis, data,
                        datum -> contains(bucket.codes(), quota.dimension() == ExamQuota.Dimension.GRADE
                                ? datum.unit().gradeCode() : datum.unit().difficultyCode()));
            }
            if (!quota.buckets().isEmpty()) {
                unclassified(result, "EXAM", quota.dimension().name(),
                        quota.enforcement() == null ? QuotaEnforcement.REQUIRED : quota.enforcement(), basis, data);
            }
        }
    }

    private static boolean contains(List<String> values, String value) {
        return value != null && values.contains(value);
    }

    private static List<BigDecimal> apportionPercentages(List<BigDecimal> percentages, BigDecimal total) {
        if (percentages.isEmpty()) return List.of();
        List<BigDecimal> fractions = new ArrayList<>(percentages.size());
        List<BigDecimal> result = new ArrayList<>(percentages.size());
        BigDecimal sum = BigDecimal.ZERO;
        int floorTotal = 0;
        for (BigDecimal percent : percentages) {
            BigDecimal value = total.multiply(percent).divide(BigDecimal.valueOf(100));
            BigDecimal floor = value.setScale(0, RoundingMode.FLOOR);
            fractions.add(value.subtract(floor));
            result.add(floor);
            floorTotal += floor.intValueExact();
            sum = sum.add(value);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < percentages.size(); i++) order.add(i);
        order.sort((a, b) -> fractions.get(b).compareTo(fractions.get(a)));
        int missing = sum.setScale(0, RoundingMode.HALF_UP).intValueExact() - floorTotal;
        for (int i = 0; i < missing; i++) {
            int index = order.get(i);
            result.set(index, result.get(index).add(BigDecimal.ONE));
        }
        return result;
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
