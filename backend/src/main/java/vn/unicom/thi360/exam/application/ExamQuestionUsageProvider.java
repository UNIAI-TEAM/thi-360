package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

/** Reverse lookup of working sources, confirmed using the same source filters as exam generation. */
@Service
public class ExamQuestionUsageProvider implements QuestionUsageProvider, ExamUsagePort {
    private final ExamBlockRefRepository refs;
    private final ExamRepository exams;
    private final ExamStructureRepository structures;
    private final CatalogLookup catalog;
    private final QuestionSourcePort source;
    private final ExamStructureCodec codec;

    public ExamQuestionUsageProvider(ExamBlockRefRepository refs, ExamRepository exams,
            ExamStructureRepository structures, CatalogLookup catalog, QuestionSourcePort source,
            ObjectMapper mapper) {
        this.refs = refs;
        this.exams = exams;
        this.structures = structures;
        this.catalog = catalog;
        this.source = source;
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
        Map<UUID, List<ExamPart>> partsByExam = new HashMap<>();
        structures.findAllById(examById.keySet()).forEach(structure ->
                partsByExam.put(structure.getExamId(), codec.read(structure.getParts()).parts()));
        Map<QuestionSourcePort.Filter, Boolean> matches = new HashMap<>();
        List<ExamUsageRef> result = new ArrayList<>();
        for (ExamBlockRef ref : blocks) {
            Exam exam = examById.get(ref.getExamId());
            if (exam == null) continue;
            for (ExamPart part : partsByExam.getOrDefault(ref.getExamId(), List.of())) {
                if (part == null || !Objects.equals(part.id(), ref.getPartId())) continue;
                if (ref.getOptionCode() == null) {
                    addSections(result, questionId, exam, part, null, part.sections(), ref,
                            knowledge, knowledgeById, descendants, matches);
                }
                for (ExamPartOption option : part.options()) {
                    if (option == null || !Objects.equals(option.code(), ref.getOptionCode())) continue;
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
        return List.copyOf(result);
    }

    private List<ExamBlockRef> candidateBlocks(UUID questionId, List<CatalogItemView> knowledge,
            Map<UUID, CatalogItemView> knowledgeById, Map<UUID, List<UUID>> descendants) {
        var description = source.describe(List.of(questionId)).get(questionId);
        if (description == null || description.subjectId() == null) return List.of();
        return refs.findBySubjectItemIdAndVersionNoIsNull(description.subjectId()).stream()
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
                    return "ALL".equals(ref.getKnowledgeMode())
                            ? groups.stream().allMatch(group -> group.stream().anyMatch(description.knowledgeIds()::contains))
                            : groups.stream().anyMatch(group -> group.stream().anyMatch(description.knowledgeIds()::contains));
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
        KnowledgeScope scope = block.knowledgeScope() != null ? block.knowledgeScope()
                : section == null ? null : section.knowledgeScope();
        List<List<UUID>> groups = scope == null ? List.of() : scope.nodeIds().stream().map(id ->
                descendants.computeIfAbsent(id, key -> {
                    CatalogItemView root = knowledgeById.get(key);
                    if (root == null) return List.of();
                    return knowledge.stream().filter(node -> node.path().startsWith(root.path()))
                            .map(CatalogItemView::id).toList();
                })).toList();
        QuestionSourcePort.Match match = scope != null && scope.match() != null
                && scope.match().name().equals("ALL") ? QuestionSourcePort.Match.ALL : QuestionSourcePort.Match.ANY;
        List<QuestionType> answers = block.questionType() == null && section != null && section.answerQuotas() != null
                ? section.answerQuotas().items().stream().map(a -> a.answerType()).filter(Objects::nonNull).toList()
                : List.of();
        var filter = new QuestionSourcePort.Filter(ref.getSubjectItemId(), groups, match, answers,
                block.questionType(), block.taskTypeCode(), block.childAnswerType(), block.tagCodes(),
                scope == null ? List.of() : scope.competencyCodes(), block.requiredPointsPerQuestion());
        boolean included = matches.computeIfAbsent(filter, key -> source.findCandidates(key, Integer.MAX_VALUE)
                .stream().anyMatch(candidate -> candidate.questionId().equals(questionId)
                        || candidate.units().stream().anyMatch(unit -> unit.questionId().equals(questionId))));
        if (included) {
            result.add(new ExamUsageRef(exam.getId(), exam.getCode(), exam.getName(), exam.getStatus(),
                    part.id(), part.name(), option == null ? null : option.id(),
                    option == null ? null : option.code(), option == null ? null : option.name(),
                    section == null ? null : section.id(), block.id(), block.name()));
        }
    }
}
