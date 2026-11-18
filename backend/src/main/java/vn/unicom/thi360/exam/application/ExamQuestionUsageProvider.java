package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamBlockRef;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamStructureRepository;
import vn.unicom.thi360.exam.spi.ExamUsagePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

/** Reverse lookup of working and serving sources using exam-generation filters. */
@Service
public class ExamQuestionUsageProvider implements QuestionUsageProvider, ExamUsagePort {
    private final ExamBlockRefRepository refs;
    private final ExamRepository exams;
    private final ExamStructureRepository structures;
    private final CatalogLookup catalog;
    private final QuestionSourcePort source;
    private final ExamStructureCodec codec;
    private final NamedParameterJdbcTemplate jdbc;

    public ExamQuestionUsageProvider(ExamBlockRefRepository refs, ExamRepository exams,
            ExamStructureRepository structures, CatalogLookup catalog, QuestionSourcePort source,
            ObjectMapper mapper, NamedParameterJdbcTemplate jdbc) {
        this.refs = refs;
        this.exams = exams;
        this.structures = structures;
        this.catalog = catalog;
        this.source = source;
        this.jdbc = jdbc;
        this.codec = new ExamStructureCodec(mapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamUsageRef> examUsagesOf(UUID questionId) {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        Map<UUID, CatalogItemView> knowledgeById = new HashMap<>();
        knowledge.forEach(item -> knowledgeById.put(item.id(), item));
        Map<UUID, List<UUID>> descendants = new HashMap<>();
        List<ExamBlockRef> blocks = candidateBlocks(questionId, knowledge, knowledgeById, descendants);
        if (blocks.isEmpty()) return List.of();
        Map<UUID, Exam> examById = new HashMap<>();
        exams.findAllById(blocks.stream().map(ExamBlockRef::getExamId).distinct().toList())
                .forEach(exam -> examById.put(exam.getId(), exam));
        if (examById.isEmpty()) return List.of();
        Map<UUID, List<ExamPart>> partsByExam = new HashMap<>();
        structures.findAllById(examById.keySet()).forEach(structure ->
                partsByExam.put(structure.getExamId(), codec.read(structure.getParts()).parts()));
        Map<VersionKey, ServingSnapshot> serving = new HashMap<>();
        jdbc.query("""
                select exam_id, version_no, parts::text, serving_path_ids
                from exam.exam_version
                where exam_id in (:examIds) and state = 'SERVING' and cardinality(serving_path_ids) > 0
                """, new MapSqlParameterSource("examIds", examById.keySet()), rs -> {
            var key = new VersionKey(rs.getObject(1, UUID.class), rs.getInt(2));
            serving.put(key, new ServingSnapshot(codec.read(rs.getString(3)).parts(),
                    List.of((String[]) rs.getArray(4).getArray())));
        });
        Map<QuestionSourcePort.Filter, Boolean> matches = new HashMap<>();
        List<ExamUsageRef> result = new ArrayList<>();
        for (ExamBlockRef ref : blocks.stream()
                .sorted(java.util.Comparator.comparing(ref -> ref.getVersionNo() != null)).toList()) {
            Exam exam = examById.get(ref.getExamId());
            if (exam == null) continue;
            ServingSnapshot snapshot = ref.getVersionNo() == null ? null
                    : serving.get(new VersionKey(ref.getExamId(), ref.getVersionNo()));
            if (ref.getVersionNo() != null && snapshot == null) continue;
            List<ExamPart> parts = snapshot == null ? partsByExam.getOrDefault(ref.getExamId(), List.of())
                    : snapshot.parts();
            for (ExamPart part : parts) {
                if (part == null || !Objects.equals(part.id(), ref.getPartId())) continue;
                if (ref.getOptionCode() == null) {
                    addSections(result, questionId, exam, part, null, part.sections(), ref,
                            knowledge, knowledgeById, descendants, matches);
                    addSections(result, questionId, exam, part, null, part.subjectPool(), ref,
                            knowledge, knowledgeById, descendants, matches);
                }
                for (ExamPartOption option : part.options()) {
                    if (option == null || !Objects.equals(option.code(), ref.getOptionCode())) continue;
                    if (snapshot != null && snapshot.paths().stream().noneMatch(path ->
                            List.of(path.split("\\+", -1)).contains(option.id()))) continue;
                    addSections(result, questionId, exam, part, option, option.sections(), ref,
                            knowledge, knowledgeById, descendants, matches);
                    if (ref.getSectionId() == null) {
                        for (QuestionBlock block : option.blocks()) {
                            addBlock(result, questionId, exam, part, option, null, block, ref,
                                    knowledge, knowledgeById, descendants, matches);
                        }
                    }
                }
            }
        }
        Map<BlockKey, ExamUsageRef> unique = new java.util.LinkedHashMap<>();
        for (ExamUsageRef usage : result) {
            unique.putIfAbsent(new BlockKey(usage.examId(), usage.partId(), usage.optionId(),
                    usage.sectionId(), usage.blockId()), usage);
        }
        return List.copyOf(unique.values());
    }

    private List<ExamBlockRef> candidateBlocks(UUID questionId, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        var description = source.describe(List.of(questionId)).get(questionId);
        if (description == null || description.subjectId() == null) return List.of();
        return refs.findBySubjectItemId(description.subjectId()).stream()
                .filter(ref -> {
                    if (ref.getQuestionType() != null) {
                        if (!ref.getQuestionType().equals(description.questionType().name())) return false;
                    } else {
                        if (description.questionType() == QuestionType.QUESTION_SET
                                || description.questionType() == QuestionType.CLOZE) return false;
                        if (ref.getAnswerTypes() != null && !ref.getAnswerTypes().isEmpty()
                                && description.answerTypes().stream()
                                    .noneMatch(type -> ref.getAnswerTypes().contains(type.name()))) return false;
                    }
                    List<UUID> ids = ref.getKnowledgeItemIds();
                    if (ids == null || ids.isEmpty()) return true;
                    var groups = ids.stream().map(id -> descendants.computeIfAbsent(id, key -> {
                        CatalogItemView root = knowledgeById.get(key);
                        if (root == null) return List.of();
                        return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                                .map(CatalogItemView::id).toList();
                    })).toList();
                    return groups.stream().anyMatch(group ->
                            group.stream().anyMatch(description.knowledgeIds()::contains));
                }).toList();
    }

    private void addSections(List<ExamUsageRef> result, UUID questionId, Exam exam, ExamPart part,
            ExamPartOption option, List<ExamSection> sections, ExamBlockRef ref,
            List<CatalogItemView> knowledge, Map<UUID, CatalogItemView> knowledgeById,
            Map<UUID, List<UUID>> descendants, Map<QuestionSourcePort.Filter, Boolean> matches) {
        for (ExamSection section : sections) {
            if (section == null || !Objects.equals(section.id(), ref.getSectionId())) continue;
            for (QuestionBlock block : section.blocks()) {
                addBlock(result, questionId, exam, part, option, section, block, ref,
                        knowledge, knowledgeById, descendants, matches);
            }
        }
    }

    private void addBlock(List<ExamUsageRef> result, UUID questionId, Exam exam, ExamPart part,
            ExamPartOption option, ExamSection section, QuestionBlock block, ExamBlockRef ref,
            List<CatalogItemView> knowledge, Map<UUID, CatalogItemView> knowledgeById,
            Map<UUID, List<UUID>> descendants, Map<QuestionSourcePort.Filter, Boolean> matches) {
        if (block == null || !Objects.equals(block.id(), ref.getBlockId())) return;
        KnowledgeScope scope = EffectiveKnowledgeScope.of(block.knowledgeScope(),
                section == null ? null : section.knowledgeScope());
        List<List<UUID>> groups = scope == null ? List.of() : scope.nodeIds().stream().map(id ->
                descendants.computeIfAbsent(id, key -> {
                    CatalogItemView root = knowledgeById.get(key);
                    if (root == null) return List.of();
                    return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                            .map(CatalogItemView::id).toList();
                })).toList();
        List<QuestionType> answers = block.questionType() == null && section != null && section.answerQuotas() != null
                ? section.answerQuotas().items().stream().map(a -> a.answerType()).filter(Objects::nonNull).toList()
                : List.of();
        var slots = effectiveSlots(block);
        boolean included = slots.stream().anyMatch(slot -> {
            KnowledgeScope slotScope = slot == null ? null : slot.knowledgeScope();
            List<List<List<UUID>>> slotGroups = slotScope == null || slotScope.nodeIds().isEmpty()
                    ? List.of() : List.of(slotScope.nodeIds().stream().map(id ->
                            descendants.computeIfAbsent(id, key -> {
                                CatalogItemView root = knowledgeById.get(key);
                                if (root == null) return List.of();
                                return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                                        .map(CatalogItemView::id).toList();
                            })).toList());
            var filter = new QuestionSourcePort.Filter(ref.getSubjectItemId(), groups, answers,
                    block.questionType(), null, block.childAnswerType(), block.tagCodes(),
                    scope == null ? List.of() : scope.competencyCodes(), block.requiredPointsPerQuestion(),
                    null, null, null,
                    slot == null || slot.tagCodes().isEmpty() ? List.of() : List.of(slot.tagCodes()),
                    slotGroups,
                    slotScope == null || slotScope.competencyCodes().isEmpty()
                            ? List.of() : List.of(slotScope.competencyCodes()),
                    scope == null ? null : scope.everyChildInScope(),
                    slotGroups.isEmpty() ? List.of() : List.of(Boolean.TRUE.equals(slotScope.everyChildInScope())));
            return matches.computeIfAbsent(filter,
                    key -> source.matching(key, List.of(questionId)).contains(questionId));
        });
        if (included) {
            result.add(new ExamUsageRef(exam.getId(), exam.getCode(), exam.getName(), exam.getStatus(),
                    part.id(), part.name(), option == null ? null : option.id(),
                    option == null ? null : option.code(), option == null ? null : option.name(),
                    section == null ? null : section.id(), block.id(), block.name()));
        }
    }

    /**
     * Declared slot indices in 1..setCount (first declaration wins), plus one null entry, the group's
     * own conditions, when any valid index is undeclared. Never sized by the nominal setCount.
     */
    private static List<vn.unicom.thi360.exam.domain.BlockSlot> effectiveSlots(QuestionBlock block) {
        if (block.slots().isEmpty()) return java.util.Collections.singletonList(null);
        if (block.setCount() == null) return block.slots();
        int setCount = block.setCount();
        java.util.Map<Integer, vn.unicom.thi360.exam.domain.BlockSlot> declared = new java.util.LinkedHashMap<>();
        for (var slot : block.slots()) {
            if (slot.index() != null && slot.index() >= 1 && slot.index() <= setCount) {
                declared.putIfAbsent(slot.index(), slot);
            }
        }
        List<vn.unicom.thi360.exam.domain.BlockSlot> result = new ArrayList<>(declared.values());
        if (declared.size() < setCount) result.add(null);
        return result;
    }

    private record VersionKey(UUID examId, int version) {}
    private record ServingSnapshot(List<ExamPart> parts, List<String> paths) {}
    private record BlockKey(UUID examId, String partId, String optionId, String sectionId, String blockId) {}
}
