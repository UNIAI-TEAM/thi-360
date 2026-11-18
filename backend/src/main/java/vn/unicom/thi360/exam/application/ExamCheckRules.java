package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.MatrixMode;
import vn.unicom.thi360.exam.domain.MatrixUnit;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.QuotaBasis;
import vn.unicom.thi360.exam.domain.QuotaEnforcement;
import vn.unicom.thi360.exam.domain.TopicQuota;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Structural and exact-quota checks; source feasibility remains the solver's responsibility. */
public final class ExamCheckRules {
    /**
     * Facts about the standard template that the exam itself does not carry. {@code unpublishableSubject} is the
     * subject whose standard template has {@code publishable = false}; {@code lockedTemplate} is the loaded
     * template's structure when it has {@code fixed_counts = true}. Either is null when it does not apply.
     */
    public record TemplateFacts(String unpublishableSubject, ExamStructureDocument lockedTemplate) {
        public static final TemplateFacts NONE = new TemplateFacts(null, null);
    }

    private final Exam exam;
    private final ExamStructureDocument document;
    private final ExamCheckIssues issues;
    private final Map<String, Map<String, List<String>>> combinationsByPart;
    private final Map<UUID, UUID> topicParents;
    private final TemplateFacts template;
    private final Map<String, SectionContext> sections = new LinkedHashMap<>();
    private final Map<String, ExamPartOption> options = new LinkedHashMap<>();
    private final Map<String, ExamPart> parts = new LinkedHashMap<>();

    ExamCheckRules(ObjectMapper mapper, Exam exam, ExamStructureDocument document) {
        this(mapper, exam, document, Map.of());
    }

    /** Definitions are indexed by part ID, then combination code; values are ordered subject codes. */
    public ExamCheckRules(ObjectMapper mapper, Exam exam, ExamStructureDocument document,
                          Map<String, Map<String, List<String>>> combinationsByPart) {
        this(mapper, exam, document, combinationsByPart, Map.of());
    }

    /** Topic parents map each knowledge node to its direct parent; roots are absent. */
    public ExamCheckRules(ObjectMapper mapper, Exam exam, ExamStructureDocument document,
                          Map<String, Map<String, List<String>>> combinationsByPart,
                          Map<UUID, UUID> topicParents) {
        this(mapper, exam, document, combinationsByPart, topicParents, TemplateFacts.NONE);
    }

    public ExamCheckRules(ObjectMapper mapper, Exam exam, ExamStructureDocument document,
                          Map<String, Map<String, List<String>>> combinationsByPart,
                          Map<UUID, UUID> topicParents, TemplateFacts template) {
        this.template = template;
        this.topicParents = topicParents;
        this.combinationsByPart = combinationsByPart;
        this.exam = exam;
        this.document = document;
        this.issues = new ExamCheckIssues(mapper);
        for (ExamPart part : document.parts()) {
            parts.put(part.id(), part);
            for (ExamSection section : part.sections()) sections.put(section.id(), new SectionContext(part, null, section));
            for (ExamSection section : part.subjectPool()) sections.put(section.id(), new SectionContext(part, null, section));
            for (ExamPartOption option : part.options()) {
                options.put(option.id(), option);
                for (ExamSection section : option.sections()) sections.put(section.id(), new SectionContext(part, option.id(), section));
                if (!option.blocks().isEmpty()) {
                    int count = option.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
                    ExamSection synthetic = new ExamSection(option.id() + ":blocks", option.code(), count,
                            null, null, null, null, option.blocks());
                    sections.put(synthetic.id(), new SectionContext(part, option.id(), synthetic));
                }
            }
        }
    }

    /** Static checks applicable to one resolved path, without validating unrelated options. */
    public static List<ObjectNode> issuesForPath(ObjectMapper mapper, Exam exam,
                                                 ExamStructureDocument document, PathResult result) {
        ExamCheckRules rules = new ExamCheckRules(mapper, exam, document);
        List<ObjectNode> found = new ArrayList<>(rules.common());
        Set<String> selected = new LinkedHashSet<>();
        result.path().chosen().forEach(choice -> selected.add(choice.optionId()));
        for (ObjectNode issue : rules.unrepresentable()) {
            String optionId = issue.path("optionId").asText(null);
            if (optionId == null || selected.contains(optionId)) found.add(issue);
        }
        found.addAll(rules.path(result));
        return dedupe(found);
    }

    List<ObjectNode> common() {
        List<ObjectNode> found = new ArrayList<>();
        if (document.parts().isEmpty() || exam.getName() == null || exam.getName().isBlank()
                || exam.getCode() == null || exam.getCode().isBlank()
                || ("PART".equals(exam.getScope()) && (exam.getPartCode() == null || exam.getPartCode().isBlank()))) {
            found.add(issue("INFO_MISSING", "INFO", null, null, null));
        }
        if (exam.getStructureYear() != null && exam.getMatrixYear() != null
                && !exam.getStructureYear().equals(exam.getMatrixYear()) && !exam.isYearsConfirmed()) {
            ObjectNode issue = issue("YEARS_UNCONFIRMED", "INFO", null, null, null);
            issue.put("required", exam.getStructureYear()); issue.put("available", exam.getMatrixYear()); found.add(issue);
        }
        for (ExamPart part : document.parts()) {
            if (isChoice(part) && part.codeSource() == null && part.options().isEmpty()) found.add(issue("OPTION_EMPTY", "STRUCTURE", part, null, null));
            if ("SHARED".equals(exam.getTiming()) && part.durationMinutes() != null && part.durationMinutes() != 0) {
                found.add(issue("SHARED_PART_DURATION", "STRUCTURE", part, null, null));
            }
        }
        for (SectionContext context : sections.values()) {
            for (QuestionBlock block : context.section.blocks()) {
                String code = Boolean.TRUE.equals(block.omitted()) ? "OMITTED_GROUPS_PRESENT"
                        : block.questionType() == QuestionType.ESSAY || block.childAnswerType() == QuestionType.ESSAY
                                ? "MANUAL_GRADING_REQUIRED" : null;
                if (code != null) {
                    ObjectNode warning = sectionIssue(code, "STRUCTURE", context, null);
                    blockContext(warning, block);
                    warning.put("severity", "WARNING");
                    found.add(issues.key(warning));
                }
            }
        }
        found.addAll(templateIssues());
        return found;
    }

    /** Template-driven errors: publish ban (D11) and locked structure counts (H27). */
    private List<ObjectNode> templateIssues() {
        List<ObjectNode> found = new ArrayList<>();
        if (template.unpublishableSubject() != null) {
            ObjectNode failure = issue("TEMPLATE_NOT_PUBLISHABLE", "STRUCTURE", null, null, null);
            failure.put("subjectCode", template.unpublishableSubject());
            found.add(failure);
        }
        if (template.lockedTemplate() != null) found.addAll(lockedCountIssues(template.lockedTemplate()));
        return found;
    }

    /**
     * Compares part and section counts with the loaded template. Elements are paired by id; when an id has no match
     * (a duplicate regenerates every id, a PUT may send new ones) the pair falls back to the same position (part
     * index, section index inside its list). A template slot without a counterpart and an extra element are both
     * violations. A part is reported only when its own count differs and none of its sections are reported, so one
     * edited section yields one issue located at that section.
     */
    private List<ObjectNode> lockedCountIssues(ExamStructureDocument locked) {
        List<ObjectNode> found = new ArrayList<>();
        for (Slot<ExamPart> slot : pair(locked.parts(), document.parts(), ExamPart::id)) {
            if (slot.current() == null) {
                ObjectNode failure = issue("STRUCTURE_COUNT_LOCKED", "STRUCTURE", null, null, null);
                failure.put("required", slot.locked().questionCount());
                failure.put("available", 0);
                failure.put("key", failure.path("key").asText() + "|missing:" + slot.name(slot.locked().id()));
                found.add(failure);
            } else if (slot.locked() == null) {
                ObjectNode failure = issue("STRUCTURE_COUNT_LOCKED", "STRUCTURE", slot.current(), null, null);
                failure.put("required", 0);
                failure.put("available", slot.current().questionCount());
                found.add(failure);
            } else {
                lockedPart(found, slot.locked(), slot.current());
            }
        }
        return found;
    }

    private void lockedPart(List<ObjectNode> found, ExamPart base, ExamPart part) {
        boolean changed = lockedSections(found, part, null, base.sections(), part.sections());
        changed |= lockedSections(found, part, null, base.subjectPool(), part.subjectPool());
        for (Slot<ExamPartOption> slot : pair(base.options(), part.options(), ExamPartOption::id)) {
            if (slot.current() == null) {
                changed |= lockedSections(found, part, slot.locked().id(), slot.locked().sections(), List.of());
            } else {
                List<ExamSection> expected = slot.locked() == null ? List.of() : slot.locked().sections();
                changed |= lockedSections(found, part, slot.current().id(), expected, slot.current().sections());
            }
        }
        if (!changed && !Objects.equals(part.questionCount(), base.questionCount())) {
            ObjectNode failure = issue("STRUCTURE_COUNT_LOCKED", "STRUCTURE", part, null, null);
            failure.put("required", base.questionCount());
            failure.put("available", part.questionCount());
            found.add(failure);
        }
    }

    /** Reports the differing slots of one section list; the result tells whether any section was reported. */
    private boolean lockedSections(List<ObjectNode> found, ExamPart part, String optionId,
                                   List<ExamSection> expected, List<ExamSection> actual) {
        boolean changed = false;
        for (Slot<ExamSection> slot : pair(expected, actual, ExamSection::id)) {
            ExamSection original = slot.locked();
            ExamSection section = slot.current();
            ObjectNode failure;
            if (section == null) {
                failure = issue("STRUCTURE_COUNT_LOCKED", "STRUCTURE", part, optionId, null);
                failure.put("required", original.questionCount() != null ? original.questionCount() : Integer.valueOf(groupTotal(original)));
                failure.put("available", 0);
                failure.put("key", failure.path("key").asText() + "|missing:" + slot.name(original.id()));
            } else {
                SectionContext context = sections.get(section.id());
                if (context == null) continue;
                if (original == null) {
                    failure = sectionIssue("STRUCTURE_COUNT_LOCKED", "STRUCTURE", context, null);
                    failure.put("required", 0);
                    failure.put("available", section.questionCount() != null ? section.questionCount() : Integer.valueOf(groupTotal(section)));
                } else {
                    boolean declared = !Objects.equals(section.questionCount(), original.questionCount());
                    int actualTotal = groupTotal(section);
                    int expectedTotal = groupTotal(original);
                    if (!declared && actualTotal == expectedTotal) continue;
                    failure = sectionIssue("STRUCTURE_COUNT_LOCKED", "STRUCTURE", context, null);
                    failure.put("required", declared ? original.questionCount() : Integer.valueOf(expectedTotal));
                    failure.put("available", declared ? section.questionCount() : Integer.valueOf(actualTotal));
                }
            }
            changed = true;
            found.add(failure);
        }
        return changed;
    }

    /**
     * Questions the groups of a section really take: explicit counts, plus the remainder of the declared section
     * count for a "take the remainder" group (the same rule as the solver), so explicit 30 and remainder over 30 agree.
     */
    private static int groupTotal(ExamSection section) {
        int fixed = section.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
        boolean remainder = section.blocks().stream().anyMatch(block ->
                !Boolean.TRUE.equals(block.omitted()) && block.questionCount() == null && !isSet(block));
        return remainder && section.questionCount() != null ? Math.max(fixed, section.questionCount()) : fixed;
    }

    /** A template element and the element of the exam that corresponds to it; either side is null when unmatched. */
    private record Slot<T>(T locked, T current, int index) {
        String name(String id) { return id == null || id.isBlank() ? "#" + index : id; }
    }

    /** Pairs by id first, then by position among the elements not claimed by an id; leftovers become one-sided slots. */
    private static <T> List<Slot<T>> pair(List<T> locked, List<T> current, java.util.function.Function<T, String> id) {
        Map<String, Integer> byId = new HashMap<>();
        for (int i = 0; i < current.size(); i++) {
            String key = id.apply(current.get(i));
            if (key != null && !key.isBlank()) byId.putIfAbsent(key, i);
        }
        Set<Integer> claimed = new LinkedHashSet<>();
        Integer[] match = new Integer[locked.size()];
        for (int i = 0; i < locked.size(); i++) {
            String key = id.apply(locked.get(i));
            Integer found = key == null ? null : byId.get(key);
            if (found != null && claimed.add(found)) match[i] = found;
        }
        List<Slot<T>> result = new ArrayList<>();
        for (int i = 0; i < locked.size(); i++) {
            Integer at = match[i];
            if (at == null && i < current.size() && claimed.add(i)) at = i;
            result.add(new Slot<>(locked.get(i), at == null ? null : current.get(at), i));
        }
        for (int i = 0; i < current.size(); i++) {
            if (!claimed.contains(i)) result.add(new Slot<>(null, current.get(i), i));
        }
        return result;
    }

    /** Find catalog combination subjects absent from the part's code-sourced subject pool. */
    public List<ObjectNode> combinationIssues() {
        List<ObjectNode> found = new ArrayList<>();
        for (ExamPart part : document.parts()) {
            if (part.codeSource() == null) continue;
            Map<String, List<String>> combinations = combinationsByPart.getOrDefault(part.id(), Map.of());
            Set<String> available = new LinkedHashSet<>();
            part.subjectPool().forEach(section -> available.add(section.subjectCode()));
            Map<String, ObjectNode> missing = new LinkedHashMap<>();
            combinations.forEach((code, subjects) -> {
                for (String subject : subjects) {
                    if (available.contains(subject)) continue;
                    ObjectNode failure = missing.computeIfAbsent(subject, ignored -> {
                        ObjectNode value = issue("COMBINATION_SUBJECT_MISSING", "STRUCTURE", part, null, null);
                        value.put("subjectCode", subject);
                        value.put("key", value.path("key").asText() + "|" + subject);
                        return value;
                    });
                    ArrayNode codes = failure.withArray("affectedCodes");
                    boolean present = false;
                    for (var value : codes) if (code.equals(value.asText())) present = true;
                    if (!present) codes.add(code);
                }
            });
            found.addAll(missing.values());
        }
        return found;
    }

    /** Detect inputs that the solver's strict value records cannot represent, including old jsonb. */
    List<ObjectNode> unrepresentable() {
        List<ObjectNode> found = new ArrayList<>();
        found.addAll(combinationIssues());
        for (ExamPart part : document.parts()) {
            if (isChoice(part) && part.codeSource() == null && (part.participation().choose() == null || part.participation().choose() < 1
                    || part.participation().choose() > part.options().size())) {
                found.add(issue("OPTION_EMPTY", "STRUCTURE", part, null, null));
            }
            for (ExamPartOption option : part.options()) {
                if (!option.blocks().isEmpty() && option.kind() != OptionKind.SUBJECT) {
                    found.add(issue("OPTION_EMPTY", "STRUCTURE", part, option.id(), null));
                }
                if (!option.blocks().isEmpty() && option.kind() == OptionKind.SUBJECT
                        && option.blocks().stream().anyMatch(block -> !isSet(block) && block.questionCount() == null)) {
                    found.add(issue("SECTION_TOTAL_MISMATCH", "STRUCTURE", part, option.id(), null));
                }
                if (option.kind() == OptionKind.SUBJECT_CHOICE) {
                    ObjectNode invalid = choiceIssue(part, option);
                    if (invalid != null) found.add(invalid);
                    if (invalid == null && option.countsByPick().stream().distinct().count() > 1) {
                        int fullCount = option.countsByPick().stream().mapToInt(Integer::intValue).max().orElse(0);
                        for (ExamSection section : option.sections()) {
                            int count = section.questionCount() == null ? fullCount : section.questionCount();
                            int fixed = section.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
                            int singles = 0;
                            for (QuestionBlock block : section.blocks()) {
                                if (Boolean.TRUE.equals(block.omitted()) || isSet(block)) continue;
                                singles += block.questionCount() == null ? Math.max(0, count - fixed) : block.questionCount();
                            }
                            if (singles < 2) {
                                ObjectNode failure = issue("SUBJECT_CHOICE_TRIM_IMPOSSIBLE", "STRUCTURE", part, option.id(), section);
                                failure.put("required", 2);
                                failure.put("available", singles);
                                found.add(failure);
                            }
                        }
                    }
                }
            }
        }
        for (SectionContext context : sections.values()) {
            ExamSection section = context.section;
            if (section.id() == null || section.subjectCode() == null) {
                found.add(issue("INFO_MISSING", "INFO", context.part, context.optionId, section));
            }
            long remainders = section.blocks().stream().filter(block -> !Boolean.TRUE.equals(block.omitted()) && block.questionCount() == null && !isSet(block)).count();
            if (remainders > 1) found.add(issue("SECTION_TOTAL_MISMATCH", "STRUCTURE", context.part, context.optionId, section));
            if (section.answerQuotas() != null) {
                Set<QuestionType> answerTypes = new LinkedHashSet<>();
                for (var value : section.answerQuotas().items()) {
                    if (value.answerType() == null) found.add(issue("INFO_MISSING", "INFO", context.part, context.optionId, section));
                    if (value.answerType() != null && !answerTypes.add(value.answerType())) {
                        ObjectNode duplicate = sectionIssue("ANSWER_QUOTA_DUPLICATE", "MATRIX", context, null);
                        duplicate.put("answerType", value.answerType().name());
                        found.add(issues.key(duplicate));
                    }
                    if (value.value() != null && value.value().signum() < 0) found.add(negative(context, null, value.value(), null));
                    if (notInteger(value.value())) found.add(nonInteger(context, null, value.value(), null));
                }
            }
            fractionalMatrix(found, context, null, section.matrix());
            topicQuotas(found, context, null, section.topicQuotas());
            for (QuestionBlock block : section.blocks()) {
                if (Boolean.TRUE.equals(block.omitted())) continue;
                if (block.childConditions().stream().anyMatch(condition -> condition.from() == null || condition.to() == null
                        || condition.from() < 1 || condition.from() > condition.to()
                        || condition.to() > QuestionSourcePort.MAX_SET_CHILDREN)) {
                    ObjectNode invalid = sectionIssue("SECTION_INCOMPLETE", "STRUCTURE", context, null);
                    blockContext(invalid, block); found.add(issues.key(invalid));
                }
                if (block.id() == null) found.add(issue("OPTION_EMPTY", "STRUCTURE", context.part, context.optionId, section));
                if (isSet(block) && (block.setCount() == null || block.setCount() <= 0
                        || block.setSize() == null || block.setSize() <= 0)) {
                    ObjectNode invalid = sectionIssue("SET_SIZE_MISMATCH", "QUESTION_SET", context, null);
                    blockContext(invalid, block); found.add(issues.key(invalid));
                }
                topicQuotas(found, context, block, block.topicQuotas());
                fractionalMatrix(found, context, block, block.matrix());
            }
        }
        return found;
    }

    private void topicQuotas(List<ObjectNode> found, SectionContext context, QuestionBlock block,
                             List<TopicQuota> quotas) {
        for (TopicQuota quota : quotas) {
            if (quota.enforcement() != QuotaEnforcement.REFERENCE && quota.basis() != null
                    && quota.basis() != QuotaBasis.QUESTION) {
                ObjectNode failure = sectionIssue("REQUIRED_BASIS_UNSUPPORTED", "MATRIX", context, null);
                if (block != null) blockContext(failure, block);
                found.add(issues.key(failure));
            }
            if (quota.enforcement() != QuotaEnforcement.REFERENCE && quota.nodeIds().isEmpty()) {
                ObjectNode failure = sectionIssue("TOPIC_QUOTA_MAPPING_MISSING", "MATRIX", context, null);
                if (block != null) blockContext(failure, block);
                issues.key(failure);
                if (quota.id() != null) failure.put("key", failure.path("key").asText() + "|" + quota.id());
                found.add(failure);
            }
        }
        for (int i = 0; i < quotas.size(); i++) {
            for (int j = i + 1; j < quotas.size(); j++) {
                for (UUID first : quotas.get(i).nodeIds()) for (UUID second : quotas.get(j).nodeIds()) {
                    if (isAncestor(first, second) || isAncestor(second, first)) {
                        ObjectNode failure = sectionIssue("TOPIC_QUOTA_NESTED", "MATRIX", context, null);
                        if (block != null) blockContext(failure, block);
                        found.add(issues.key(failure));
                        return;
                    }
                }
            }
        }
    }

    private boolean isAncestor(UUID ancestor, UUID node) {
        Set<UUID> visited = new LinkedHashSet<>();
        UUID current = topicParents.get(node);
        while (current != null && visited.add(current)) {
            if (current.equals(ancestor)) return true;
            current = topicParents.get(current);
        }
        return false;
    }
    /** Identifies a selected question or group; affected codes must already be restricted to its path. */
    public record EvidenceLocation(String partId, String sectionId, String subjectCode, String blockId,
                                   Integer slotIndex, Integer childPosition, String pathId,
                                   List<String> affectedCodes) { }

    /** Null fact means unavailable, not false. Facts must describe a question in the proved witness. */
    public record WitnessFact(EvidenceLocation location, String questionCode, Boolean hasAnswerKey,
                              Boolean hasScoringRule, Boolean attachmentsReadable) { }

    public enum FilterKind { ITEM_SHAPE, ANSWER_FORMAT, CHILD_CONDITION }

    /** Counts use the selection unit (individual questions or complete sets), with only this filter removed. */
    public record FilterEvidence(EvidenceLocation location, FilterKind kind, int required,
                                 long availableWithoutFilter, long availableWithFilter) { }

    public List<ObjectNode> witnessIssues(List<WitnessFact> facts) {
        List<ObjectNode> found = new ArrayList<>();
        for (WitnessFact fact : facts) {
            if (Boolean.FALSE.equals(fact.hasAnswerKey()) || Boolean.FALSE.equals(fact.hasScoringRule())) {
                found.add(witnessIssue("QUESTION_NOT_PLAYABLE", fact));
            }
            if (Boolean.FALSE.equals(fact.attachmentsReadable())) {
                found.add(witnessIssue("ATTACHMENT_MISSING", fact));
            }
        }
        return found;
    }

    public List<ObjectNode> filterIssues(List<FilterEvidence> evidence) {
        List<ObjectNode> found = new ArrayList<>();
        for (FilterEvidence item : evidence) {
            if (item.availableWithoutFilter() < item.required() || item.availableWithFilter() >= item.required()) continue;
            String code = switch (item.kind()) {
                case ITEM_SHAPE -> "ITEM_SHAPE_SHORTAGE";
                case ANSWER_FORMAT -> "ANSWER_FORMAT_SHORTAGE";
                case CHILD_CONDITION -> "CHILD_CONDITION_SHORTAGE";
            };
            ObjectNode failure = evidenceIssue(code, item.location());
            failure.put("required", item.required());
            failure.put("available", item.availableWithFilter());
            found.add(issues.key(failure));
        }
        return found;
    }

    /** Required-vs-available evidence for one declared topic quota (BE-4b supplies counts). */
    public record TopicQuotaEvidence(EvidenceLocation location, String quotaId, int required,
                                     long available) { }

    /** One combination's serving content: code, ordered subjects and branch (K13). */
    public record CombinationContent(String code, List<String> subjectCodes, String branch) {
        public CombinationContent {
            subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
        }
    }

    /** Topic quotas the source cannot satisfy (BE-4b wires the counts). */
    public List<ObjectNode> topicQuotaIssues(List<TopicQuotaEvidence> evidence) {
        List<ObjectNode> found = new ArrayList<>();
        for (TopicQuotaEvidence item : evidence) {
            if (item.available() >= item.required()) continue;
            ObjectNode failure = evidenceIssue("TOPIC_QUOTA_INFEASIBLE", item.location());
            failure.put("required", item.required());
            failure.put("available", item.available());
            issues.key(failure);
            if (item.quotaId() != null) failure.put("key", failure.path("key").asText() + "|" + item.quotaId());
            found.add(failure);
        }
        return found;
    }

    /**
     * Warn when the combination list a published version was solved against differs from the
     * catalog's current list by content (code, ordered subjects, branch). BE-9 supplies both lists.
     */
    public List<ObjectNode> combinationListIssues(List<CombinationContent> served,
                                                List<CombinationContent> current) {
        Map<String, CombinationContent> before = new LinkedHashMap<>();
        served.forEach(item -> before.put(item.code(), item));
        Map<String, CombinationContent> after = new LinkedHashMap<>();
        current.forEach(item -> after.put(item.code(), item));
        List<String> changed = new ArrayList<>();
        for (var entry : before.entrySet()) {
            CombinationContent now = after.get(entry.getKey());
            if (now == null || !entry.getValue().equals(now)) changed.add(entry.getKey());
        }
        for (var entry : after.entrySet()) {
            if (!before.containsKey(entry.getKey())) changed.add(entry.getKey());
        }
        if (changed.isEmpty()) return List.of();
        ObjectNode warning = issues.create("COMBINATION_LIST_CHANGED", "STRUCTURE", "WARNING");
        issues.key(warning);
        ArrayNode codes = warning.withArray("affectedCodes");
        changed.forEach(codes::add);
        return List.of(warning);
    }

    private ObjectNode witnessIssue(String code, WitnessFact fact) {
        ObjectNode failure = evidenceIssue(code, fact.location());
        failure.putArray("questionCodes").add(fact.questionCode());
        issues.key(failure);
        failure.put("key", failure.path("key").asText() + "|" + fact.questionCode());
        return failure;
    }

    private ObjectNode evidenceIssue(String code, EvidenceLocation where) {
        ObjectNode failure = issues.create(code, "SOURCE", "ERROR");
        failure.put("partId", where.partId());
        failure.put("sectionId", where.sectionId());
        failure.put("subjectCode", where.subjectCode());
        failure.put("blockId", where.blockId());
        ObjectNode location = failure.putObject("location");
        if (where.slotIndex() != null) location.put("slotIndex", where.slotIndex());
        if (where.childPosition() != null) location.put("childPosition", where.childPosition());
        if (where.pathId() != null) location.put("pathId", where.pathId());
        ArrayNode affected = failure.withArray("affectedCodes");
        if (where.affectedCodes() != null) where.affectedCodes().forEach(affected::add);
        return failure;
    }

    List<ObjectNode> path(PathResult result) {
        List<ObjectNode> found = new ArrayList<>();
        for (var selected : result.path().chosen()) {
            ExamPartOption option = options.get(selected.optionId());
            ExamPart part = parts.get(selected.partId());
            if (option == null || part == null) continue;
            if (Boolean.FALSE.equals(option.expertConfirmed())) found.add(issue("OPTION_UNCONFIRMED", "STRUCTURE", part, option.id(), null));
            if (option.kind() == OptionKind.SUBJECT_CHOICE) {
                ObjectNode invalid = choiceIssue(part, option);
                if (invalid != null) found.add(invalid);
            }
        }
        Map<String, Set<Integer>> possible = new HashMap<>();
        for (var picks : result.path().picks()) for (SubjectPick pick : picks) {
            for (int i = 0; i < pick.sections().size(); i++) {
                possible.computeIfAbsent(pick.sections().get(i).sectionId(), ignored -> new LinkedHashSet<>()).add(pick.counts().get(i));
            }
        }
        for (SectionOnPath section : result.path().sections()) {
            int count = section.questionCount() == null ? 0 : section.questionCount();
            section(found, section.sectionId(), count, Set.of(count));
        }
        for (var entry : possible.entrySet()) for (int count : entry.getValue()) section(found, entry.getKey(), count, entry.getValue());
        totals(found, result);
        return dedupe(found);
    }

    private void totals(List<ObjectNode> found, PathResult result) {
        List<List<SubjectPick>> tuples = new ArrayList<>();
        product(result.path().picks(), 0, new ArrayList<>(), tuples);
        for (List<SubjectPick> tuple : tuples) {
            List<ObjectNode> tupleIssues = new ArrayList<>();
            totalsForTuple(tupleIssues, result, tuple);
            if (!tuple.isEmpty()) {
                for (ObjectNode issue : tupleIssues) {
                    var selection = issue.putArray("selection");
                    StringBuilder suffix = new StringBuilder();
                    for (SubjectPick pick : tuple) {
                        for (int i = 0; i < pick.sections().size(); i++) {
                            String id = pick.sections().get(i).sectionId();
                            int count = pick.counts().get(i);
                            var entry = selection.addObject();
                            entry.put("sectionId", id);
                            entry.put("count", count);
                            suffix.append('|').append(id.length()).append(':').append(id).append(':').append(count);
                        }
                    }
                    issue.put("key", issue.path("key").asText() + suffix);
                }
            }
            found.addAll(tupleIssues);
        }
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

    private void totalsForTuple(List<ObjectNode> found, PathResult result, List<SubjectPick> tuple) {
        List<SectionOnPath> counted = new ArrayList<>(result.path().sections());
        for (SubjectPick pick : tuple) counted.addAll(pick.resolvedSections());
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> actualCounts = new LinkedHashMap<>();
        Map<String, BigDecimal> points = new LinkedHashMap<>();
        Set<String> missingPoints = new LinkedHashSet<>();
        Set<String> partsWithOmitted = new LinkedHashSet<>();
        Set<String> partsWithActiveBlocks = new LinkedHashSet<>();
        int total = 0;
        for (SectionOnPath section : counted) {
            SectionContext context = sections.get(section.sectionId());
            if (context == null) continue;
            int count = section.questionCount() == null ? 0 : section.questionCount();
            counts.merge(context.part.id(), count, Integer::sum);
            ExamPartOption option = context.optionId == null ? null : options.get(context.optionId);
            boolean subjectChoice = option != null && option.kind() == OptionKind.SUBJECT_CHOICE;
            if (subjectChoice) total += count;
            int fixed = context.section.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
            for (QuestionBlock block : context.section.blocks()) {
                if (Boolean.TRUE.equals(block.omitted())) {
                    partsWithOmitted.add(context.part.id());
                    continue;
                }
                partsWithActiveBlocks.add(context.part.id());
                int groupCount = isSet(block) || block.questionCount() != null
                        ? fixedCount(block) : Math.max(0, count - fixed);
                actualCounts.merge(context.part.id(), groupCount, Integer::sum);
                if (!subjectChoice) total += groupCount;
                if (groupCount == 0) {
                    missingPoints.add(context.part.id());
                    continue;
                }
                if (block.requiredPointsPerQuestion() == null) {
                    missingPoints.add(context.part.id());
                    if (exam.getMaxScore() != null) {
                        ObjectNode issue = sectionIssue("POINTS_TOTAL_MISMATCH", "POINTS", context, null);
                        blockContext(issue, block);
                        found.add(issues.key(issue));
                    }
                } else {
                    points.merge(context.part.id(), block.requiredPointsPerQuestion()
                            .multiply(BigDecimal.valueOf(groupCount)), BigDecimal::add);
                }
            }
        }
        for (SubjectPick pick : tuple) {
            ExamPartOption option = options.get(pick.optionId());
            if (option == null || option.countsByPick() == null) continue;
            int drop = pick.totalCount() - option.countsByPick().stream().mapToInt(Integer::intValue).sum();
            if (drop <= 0) continue;
            SectionContext context = pick.sections().isEmpty() ? null : sections.get(pick.sections().getFirst().sectionId());
            if (context == null) continue;
            counts.merge(context.part.id(), -drop, Integer::sum);
            actualCounts.merge(context.part.id(), -drop, Integer::sum);
            total -= drop;
            long natural = pick.sections().stream().filter(section -> "NATURAL".equals(section.choiceGroup())).count();
            long social = pick.sections().stream().filter(section -> "SOCIAL".equals(section.choiceGroup())).count();
            String trimmedGroup = natural == 0 || social == 0 ? null : natural < social ? "NATURAL" : "SOCIAL";
            List<BigDecimal> singlePoints = pick.sections().stream()
                    .filter(section -> trimmedGroup == null || trimmedGroup.equals(section.choiceGroup()))
                    .map(section -> sections.get(section.sectionId()))
                    .filter(Objects::nonNull).flatMap(section -> section.section.blocks().stream())
                    .filter(block -> !Boolean.TRUE.equals(block.omitted()) && !isSet(block))
                    .map(QuestionBlock::requiredPointsPerQuestion).filter(Objects::nonNull).distinct().toList();
            if (singlePoints.size() == 1) {
                points.merge(context.part.id(), singlePoints.getFirst().multiply(BigDecimal.valueOf(-drop)), BigDecimal::add);
            }
        }
        BigDecimal totalPoints = BigDecimal.ZERO;
        for (ExamPart part : document.parts()) {
            int count = counts.getOrDefault(part.id(), 0);
            boolean intentionallyOmitted = count == 0 && partsWithOmitted.contains(part.id())
                    && !partsWithActiveBlocks.contains(part.id());
            if (actualCounts.getOrDefault(part.id(), 0) == 0 && !intentionallyOmitted) {
                found.add(issue("PART_EMPTY", "STRUCTURE", part, null, null));
            }
            if (part.questionCount() != null && part.questionCount() != count) {
                ObjectNode issue = issue("PART_TOTAL_MISMATCH", "STRUCTURE", part, null, null);
                issue.put("required", part.questionCount()); issue.put("available", count); found.add(issue);
            }
            BigDecimal actual = points.getOrDefault(part.id(), BigDecimal.ZERO);
            totalPoints = totalPoints.add(actual);
            if (part.maxPoints() != null && !missingPoints.contains(part.id()) && differs(actual, part.maxPoints())) {
                ObjectNode issue = issue("PART_POINTS_MISMATCH", "POINTS", part, null, null);
                issue.put("required", part.maxPoints()); issue.put("available", actual); found.add(issue);
            }
        }
        if (exam.getQuestionCount() != total) {
            ObjectNode issue = issue("PATH_TOTAL_MISMATCH", "STRUCTURE", null, null, null);
            issue.put("required", exam.getQuestionCount()); issue.put("available", total); found.add(issue);
        }
        BigDecimal target = "LINEAR".equals(exam.getPracticeScoring()) ? exam.getReferenceMax() : exam.getMaxScore();
        if (target != null && missingPoints.isEmpty() && differs(totalPoints, target)) {
            ObjectNode issue = issue("POINTS_TOTAL_MISMATCH", "POINTS", null, null, null);
            issue.put("required", target); issue.put("available", totalPoints); found.add(issue);
        }
    }

    private static boolean differs(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(new BigDecimal("0.000001")) > 0;
    }

    private void section(List<ObjectNode> found, String id, int count, Set<Integer> possible) {
        SectionContext context = sections.get(id);
        if (context == null) return;
        ExamSection section = context.section;
        boolean varies = possible.size() > 1;
        int min = possible.stream().mapToInt(Integer::intValue).min().orElse(count);
        int max = possible.stream().mapToInt(Integer::intValue).max().orElse(count);
        int fixed = section.blocks().stream().mapToInt(ExamCheckRules::fixedCount).sum();
        List<QuestionBlock> remaining = section.blocks().stream().filter(block -> !Boolean.TRUE.equals(block.omitted()) && block.questionCount() == null && !isSet(block)).toList();
        if (remaining.size() > 1 || (!remaining.isEmpty() && fixed > min)) {
            ObjectNode issue = sectionIssue("SECTION_TOTAL_MISMATCH", "STRUCTURE", context, varies ? count : null);
            issue.put("required", min); issue.put("available", fixed); found.add(issues.key(issue));
        } else if (remaining.isEmpty() && fixed != count) {
            ObjectNode issue = sectionIssue("SECTION_TOTAL_MISMATCH", "STRUCTURE", context, varies ? count : null);
            issue.put("required", count); issue.put("available", fixed); found.add(issues.key(issue));
        }
        if (section.answerQuotas() == null) for (QuestionBlock block : section.blocks()) {
            if (Boolean.TRUE.equals(block.omitted())) continue;
            if (block.questionType() == null && !isSet(block)) {
                ObjectNode issue = sectionIssue("GROUP_ANSWER_TYPE_MISSING", "STRUCTURE", context, varies ? count : null);
                blockContext(issue, block); found.add(issues.key(issue));
            }
        }
        boolean countVaries = varies && ((section.answerQuotas() != null && section.answerQuotas().unit() == MatrixUnit.COUNT)
                || (section.matrix() != null && section.matrix().unit() == MatrixUnit.COUNT));
        if (section.answerQuotas() != null) {
            values(found, context, null, section.answerQuotas().items().stream().map(value -> value.value()).toList(),
                    section.answerQuotas().unit(), count, null, "ANSWER_QUOTA_TOTAL_MISMATCH", countVaries, varies ? count : null);
        }
        matrix(found, context, null, section.matrix(), count, "SUBJECT_MATRIX_TOTAL_MISMATCH", countVaries, varies ? count : null);
        for (QuestionBlock block : section.blocks()) {
            if (Boolean.TRUE.equals(block.omitted())) continue;
            int groupCount = block.questionCount() == null ? Math.max(0, count - fixed) : fixedCount(block);
            if (isSet(block) && block.questionCount() != null
                    && (block.setCount() == null || block.setSize() == null || block.setCount() * block.setSize() != block.questionCount())) {
                ObjectNode issue = sectionIssue("SET_SIZE_MISMATCH", "QUESTION_SET", context, varies ? count : null);
                blockContext(issue, block); issue.put("required", block.questionCount());
                issue.put("available", (block.setCount() == null ? 0 : block.setCount()) * (block.setSize() == null ? 0 : block.setSize()));
                found.add(issues.key(issue));
            }
            matrix(found, context, block, block.matrix(), groupCount, "MATRIX_TOTAL_MISMATCH", false, varies ? count : null);
        }
    }

    private void matrix(List<ObjectNode> found, SectionContext context, QuestionBlock block, BlockMatrix matrix,
                        int count, String countCode, boolean countVaries, Integer subjectCount) {
        if (matrix == null) return;
        if (matrix.mode() == MatrixMode.COMBINED) {
            values(found, context, block, matrix.cells().stream().map(cell -> cell.value()).toList(), matrix.unit(), count,
                    null, countCode, countVaries || matrix.gradeEnforcement() == QuotaEnforcement.REFERENCE, subjectCount);
        } else {
            values(found, context, block, matrix.byGrade().stream().map(value -> value.value()).toList(), matrix.unit(), count,
                    "GRADE", countCode, countVaries || matrix.gradeEnforcement() == QuotaEnforcement.REFERENCE, subjectCount);
            values(found, context, block, matrix.byDifficulty().stream().map(value -> value.value()).toList(), matrix.unit(), count,
                    "DIFFICULTY", countCode, countVaries || matrix.difficultyEnforcement() == QuotaEnforcement.REFERENCE, subjectCount);
        }
        if (!matrix.byCompetency().isEmpty()) values(found, context, block,
                matrix.byCompetency().stream().map(value -> value.value()).toList(), matrix.unit(), count,
                "COMPETENCY", countCode, countVaries, subjectCount);
    }

    private void values(List<ObjectNode> found, SectionContext context, QuestionBlock block, List<BigDecimal> values,
                        MatrixUnit unit, int count, String dimension, String countCode, boolean countVaries, Integer subjectCount) {
        if (values.isEmpty()) return;
        if (values.stream().anyMatch(Objects::isNull)) return; // handled before solver construction
        BigDecimal minimum = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        String code = minimum.signum() < 0 ? "MATRIX_NEGATIVE"
                : unit == MatrixUnit.PERCENT && sum.compareTo(BigDecimal.valueOf(100)) != 0 ? "PERCENT_TOTAL_INVALID"
                : unit != MatrixUnit.PERCENT && sum.compareTo(BigDecimal.valueOf(count)) != 0 && !countVaries ? countCode : null;
        if (code == null) return;
        ObjectNode issue = sectionIssue(code, "MATRIX", context, subjectCount);
        if (block != null) blockContext(issue, block);
        issue.put("dimension", dimension);
        issue.put("required", code.equals("MATRIX_NEGATIVE") ? 0 : unit == MatrixUnit.PERCENT ? 100 : count);
        issue.put("available", code.equals("MATRIX_NEGATIVE") ? minimum : sum);
        found.add(issues.key(issue));
    }

    private ObjectNode choiceIssue(ExamPart part, ExamPartOption option) {
        int choose = option.chooseSubjects() == null ? 0 : option.chooseSubjects();
        List<Integer> counts = option.countsByPick() == null ? List.of() : option.countsByPick();
        int common = part.sections().stream().mapToInt(section -> section.questionCount() == null ? 0 : section.questionCount()).sum();
        int target = (part.questionCount() == null ? 0 : part.questionCount()) - common;
        boolean invalid = choose < 1 || option.sections().size() < 2
                || choose > option.sections().size() || option.sections().size() > 8
                || counts.size() != choose || counts.stream().anyMatch(value -> value == null || value <= 0)
                || option.sections().stream().anyMatch(section -> section.choiceGroup() == null);
        // With one selected option it must fill the remainder after the common sections.
        // Multiple selected options are checked together by the path-total rule instead.
        if (!invalid && part.participation() != null
                && Integer.valueOf(1).equals(part.participation().choose())
                && part.questionCount() != null) {
            invalid = counts.stream().mapToLong(Integer::longValue).sum() != target;
        }
        if (!invalid) invalid = !allocationsMatch(option, choose, counts);
        if (!invalid) return null;
        ObjectNode issue = issue("SUBJECT_CHOICE_COUNTS_INVALID", "STRUCTURE", part, option.id(), null);
        issue.put("required", target); issue.put("available", counts.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        return issue;
    }

    private static boolean allocationsMatch(ExamPartOption option, int choose, List<Integer> counts) {
        int natural = (int) option.sections().stream().filter(section -> section.choiceGroup().name().equals("NATURAL")).count();
        int social = option.sections().size() - natural;
        int high = counts.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int low = counts.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<Integer> expected = counts.stream().sorted().toList();
        for (int n = Math.max(0, choose - social); n <= Math.min(choose, natural); n++) {
            int s = choose - n;
            if (n > 0 && s > 0 && (n == s || choose != 3)) return false;
            int lows = n == 0 || s == 0 ? 1 : Math.min(n, s);
            List<Integer> actual = new ArrayList<>(choose);
            for (int i = 0; i < choose; i++) actual.add(i < lows ? low : high);
            if (!actual.stream().sorted().toList().equals(expected)) return false;
        }
        return true;
    }

    private void fractionalMatrix(List<ObjectNode> found, SectionContext context, QuestionBlock block, BlockMatrix matrix) {
        if (matrix == null) return;
        boolean required = (!matrix.cells().isEmpty() && matrix.gradeEnforcement() == QuotaEnforcement.REQUIRED)
                || (!matrix.byGrade().isEmpty() && matrix.gradeEnforcement() == QuotaEnforcement.REQUIRED)
                || (!matrix.byDifficulty().isEmpty() && matrix.difficultyEnforcement() == QuotaEnforcement.REQUIRED)
                || !matrix.byCompetency().isEmpty();
        if (matrix.basis() != QuotaBasis.QUESTION && required) {
            ObjectNode issue = sectionIssue("REQUIRED_BASIS_UNSUPPORTED", "MATRIX", context, null);
            if (block != null) blockContext(issue, block);
            found.add(issues.key(issue));
        }
        if (matrix.mode() == null) {
            ObjectNode issue = sectionIssue("MATRIX_INCOMPLETE", "MATRIX", context, null);
            if (block != null) blockContext(issue, block);
            found.add(issues.key(issue));
        }
        boolean pointBasis = matrix.basis() == QuotaBasis.POINTS;
        matrix.cells().forEach(value -> invalidValue(found, context, block, value.value(), null,
                pointBasis && matrix.gradeEnforcement() == QuotaEnforcement.REFERENCE));
        matrix.byGrade().forEach(value -> invalidValue(found, context, block, value.value(), "GRADE",
                pointBasis && matrix.gradeEnforcement() == QuotaEnforcement.REFERENCE));
        matrix.byDifficulty().forEach(value -> invalidValue(found, context, block, value.value(), "DIFFICULTY",
                pointBasis && matrix.difficultyEnforcement() == QuotaEnforcement.REFERENCE));
        matrix.byCompetency().forEach(value -> invalidValue(found, context, block, value.value(), "COMPETENCY", false));
    }

    private void invalidValue(List<ObjectNode> found, SectionContext context, QuestionBlock block, BigDecimal value,
                              String dimension, boolean decimalAllowed) {
        if (value == null || (!decimalAllowed && notInteger(value))) found.add(nonInteger(context, block, value, dimension));
        else if (value.signum() < 0) found.add(negative(context, block, value, dimension));
    }

    private ObjectNode negative(SectionContext context, QuestionBlock block, BigDecimal value, String dimension) {
        ObjectNode issue = sectionIssue("MATRIX_NEGATIVE", "MATRIX", context, null);
        if (block != null) blockContext(issue, block);
        issue.put("dimension", dimension); issue.put("required", 0); issue.put("available", value);
        return issues.key(issue);
    }

    private ObjectNode nonInteger(SectionContext context, QuestionBlock block, BigDecimal value, String dimension) {
        ObjectNode issue = sectionIssue("MATRIX_NON_INTEGER", "MATRIX", context, null);
        if (block != null) blockContext(issue, block);
        issue.put("dimension", dimension); if (value != null) issue.put("available", value);
        return issues.key(issue);
    }

    private static boolean notInteger(BigDecimal value) {
        if (value == null) return true;
        try { value.intValueExact(); return false; } catch (ArithmeticException e) { return true; }
    }

    private ObjectNode sectionIssue(String code, String group, SectionContext context, Integer subjectCount) {
        ObjectNode issue = issue(code, group, context.part, context.optionId, context.section);
        if (subjectCount != null) issue.put("subjectCount", subjectCount);
        return issue;
    }

    private ObjectNode issue(String code, String group, ExamPart part, String optionId, ExamSection section) {
        ObjectNode issue = issues.create(code, group, "ERROR");
        if (part != null) { issue.put("partId", part.id()); issue.put("partName", part.name()); }
        issue.put("optionId", optionId);
        if (section != null) { issue.put("sectionId", section.id()); issue.put("subjectCode", section.subjectCode()); }
        return issues.key(issue);
    }

    private static void blockContext(ObjectNode issue, QuestionBlock block) { issue.put("blockId", block.id()); issue.put("blockName", block.name()); }
    private static boolean isChoice(ExamPart part) { return part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE; }
    private static boolean isSet(QuestionBlock block) { return block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE; }
    private static int fixedCount(QuestionBlock block) {
        if (Boolean.TRUE.equals(block.omitted())) return 0;
        if (block.questionCount() != null) return block.questionCount();
        return block.setCount() == null || block.setSize() == null ? 0 : block.setCount() * block.setSize();
    }
    private static List<ObjectNode> dedupe(List<ObjectNode> found) {
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        found.forEach(issue -> result.putIfAbsent(issue.path("key").asText(), issue));
        return List.copyOf(result.values());
    }
    private record SectionContext(ExamPart part, String optionId, ExamSection section) { }
}
