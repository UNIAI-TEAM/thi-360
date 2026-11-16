package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.ExamBlockRef;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.CandidateSource;
import vn.unicom.thi360.exam.domain.solver.ChosenOption;
import vn.unicom.thi360.exam.domain.solver.CompetencyQuota;
import vn.unicom.thi360.exam.domain.solver.CountingUnit;
import vn.unicom.thi360.exam.domain.solver.DimensionQuota;
import vn.unicom.thi360.exam.domain.solver.CellQuota;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.MatrixQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.QuotaUnit;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectCountAllocator;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectQuota;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.spi.ExamKnowledgeScopes;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Dựng đầu vào của {@link vn.unicom.thi360.exam.domain.solver.PaperSolver} cho xem trước một
 * đường lựa chọn của bản làm việc (spec 5.2 "Xem trước", B9): ánh xạ tài liệu cấu trúc sang
 * {@link SolvePath} (môn cố định + phương án {@code SUBJECT_CHOICE} đã giải quyết theo
 * {@code subjectCodes}) và bộ lọc nguồn của từng nhóm theo chiếu {@code exam_block_ref}.
 *
 * <p>Lớp dựng của riêng tính năng xem trước; dịch vụ kiểm tra cấu hình có bộ dựng tương đương
 * trên cùng quy tắc ánh xạ (phạm vi MAPID có hiệu lực, dạng trả lời, chỉ tiêu cấp môn).
 */
@Component
public class PreviewInputBuilder {

    /** pathId của bài không có phần tự chọn (giống web check.ts). */
    public static final String DEFAULT_PATH_ID = "default";

    private final ExamBlockRefRepository refs;
    private final ExamKnowledgeScopes scopes;
    private final CatalogLookup catalog;
    private final QuestionSourcePort source;

    public PreviewInputBuilder(ExamBlockRefRepository refs, ExamKnowledgeScopes scopes,
            CatalogLookup catalog, QuestionSourcePort source) {
        this.refs = refs;
        this.scopes = scopes;
        this.catalog = catalog;
        this.source = source;
    }

    /** Một lựa chọn của phần tự chọn trên đường (nhãn đường; giống {@code PathChoice} của web). */
    public record PathChoice(String partId, String optionId, String kind, String code,
                             String name) {
    }

    /** Tham chiếu đường đã giải quyết (pathId theo cách nối optionId của web). */
    public record PathRef(String pathId, List<PathChoice> choices) {
    }

    /** Ngữ cảnh của một nhóm lấy câu trên đường (phục vụ dựng kết quả xem trước). */
    public record GroupDetail(String partId, String partName, Integer partOrder,
                              Integer partDurationMinutes, String optionId, String sectionId,
                              String subjectCode, QuestionBlock block, ExamBlockRef ref) {
    }

    /** Nguồn ứng viên của một lần giải: lấy theo nhóm, nhớ lại để dựng đề sau khi giải. */
    public static final class PreviewCandidates implements CandidateSource {
        private final Map<GroupQuota, QuestionSourcePort.Filter> filters;
        private final QuestionSourcePort port;
        private final Map<GroupQuota, List<PathCandidate>> cache = new HashMap<>();
        private final Map<String, QuestionSourcePort.Candidate> byQuestionId = new LinkedHashMap<>();

        PreviewCandidates(Map<GroupQuota, QuestionSourcePort.Filter> filters,
                QuestionSourcePort port) {
            this.filters = filters;
            this.port = port;
        }

        @Override
        public List<PathCandidate> candidatesFor(GroupQuota group) {
            return cache.computeIfAbsent(group, g -> fetch(g).stream()
                    .map(PreviewInputBuilder::toPathCandidate).toList());
        }

        private List<QuestionSourcePort.Candidate> fetch(GroupQuota group) {
            QuestionSourcePort.Filter filter = filters.get(group);
            if (filter == null || filter.subjectId() == null) {
                return List.of();
            }
            List<QuestionSourcePort.Candidate> found = port.findCandidates(
                    filter, Integer.MAX_VALUE);
            for (QuestionSourcePort.Candidate c : found) {
                byQuestionId.put(c.questionId().toString(), c);
            }
            return found;
        }

        /** Ứng viên đã tải theo id câu (để đọc phân loại/điểm từng đơn vị khi dựng đề). */
        public Map<String, QuestionSourcePort.Candidate> byQuestionId() {
            return byQuestionId;
        }
    }

    /** Kết quả dựng: đường đã giải quyết, nhãn đường, nguồn và ngữ cảnh từng nhóm. */
    public record PreviewInput(SolvePath path, PathRef pathRef, PreviewCandidates candidates,
                               Map<String, GroupDetail> groups) {
    }

    /**
     * Giải quyết {@code pathId} trên tài liệu cấu trúc và dựng đầu vào bộ giải.
     *
     * @param subjectCodes các môn muốn xem của mọi phương án {@code SUBJECT_CHOICE} trên đường
     * @param seed         hạt giống chọn cách chia số câu (B10) và xáo thứ tự lấy câu
     * @param allocator    quy tắc chia số câu theo nhóm môn của bộ giải
     */
    public PreviewInput build(UUID examId, ExamStructureDocument doc, String pathId,
            List<String> subjectCodes, long seed, SubjectCountAllocator allocator) {
        Map<String, ExamBlockRef> refByBlockId = new HashMap<>();
        for (ExamBlockRef ref : refs.findByExamIdAndVersionNo(examId, null)) {
            refByBlockId.put(ref.getBlockId(), ref);
        }
        Map<String, ExamKnowledgeScopes.BlockScope> scopeByBlockId = new HashMap<>();
        for (ExamKnowledgeScopes.BlockScope scope : scopes.findDraftScopes(examId)) {
            scopeByBlockId.put(scope.blockId(), scope);
        }
        Map<UUID, String> itemCodeCache = new HashMap<>();
        Map<GroupQuota, QuestionSourcePort.Filter> filters = new LinkedHashMap<>();
        Map<String, GroupDetail> details = new HashMap<>();

        String[] wanted = DEFAULT_PATH_ID.equals(pathId) ? new String[0]
                : pathId.split("\\+", -1);
        int wantedIndex = 0;
        List<ChosenOption> chosen = new ArrayList<>();
        List<SectionOnPath> sections = new ArrayList<>();
        List<SubjectChoice> choices = new ArrayList<>();
        List<List<SubjectPick>> picks = new ArrayList<>();
        List<PathChoice> pathChoices = new ArrayList<>();
        List<ResolvedPart> orderedParts = new ArrayList<>();

        for (ExamPart part : sortedParts(doc)) {
            ExamPartOption option = null;
            if (isChoice(part)) {
                if (wantedIndex >= wanted.length) {
                    throw pathNotFound(pathId);
                }
                String optionId = wanted[wantedIndex++];
                option = part.options().stream().filter(o -> optionId.equals(o.id())).findFirst()
                        .orElseThrow(() -> pathNotFound(pathId));
                chosen.add(new ChosenOption(part.id(), option.id()));
                pathChoices.add(new PathChoice(part.id(), option.id(),
                        option.kind() == null ? null : option.kind().name(), option.code(),
                        option.name() != null ? option.name() : option.code()));
            }
            orderedParts.add(new ResolvedPart(part, option));

            for (ExamSection section : part.sections()) {
                sections.add(sectionOnPath(section, refByBlockId, scopeByBlockId,
                        itemCodeCache, filters, details, part, null, false));
            }
            if (option == null) {
                continue;
            }
            if (option.kind() == OptionKind.SUBJECT_CHOICE) {
                List<SectionOnPath> choiceSections = new ArrayList<>();
                for (ExamSection section : option.sections()) {
                    choiceSections.add(sectionOnPath(section, refByBlockId,
                            scopeByBlockId, itemCodeCache, filters, details, part,
                            option.id(), true));
                }
                SubjectChoice choice = new SubjectChoice(option.id(), choiceSections,
                        chooseOf(option), countsOf(option));
                choices.add(choice);
                picks.add(List.of(resolvePick(choice, subjectCodes, seed, allocator)));
            } else {
                for (ExamSection section : option.sections()) {
                    sections.add(sectionOnPath(section, refByBlockId, scopeByBlockId,
                            itemCodeCache, filters, details, part, option.id(), false));
                }
            }
            attachOptionBlocks(part, option, sections, refByBlockId, scopeByBlockId,
                    itemCodeCache, filters, details);
        }
        if (wantedIndex != wanted.length) {
            throw pathNotFound(pathId);
        }
        SolvePath path = new SolvePath(0, chosen, sections, choices, picks);
        PreviewCandidates candidates = new PreviewCandidates(filters, source);
        return new PreviewInput(path, new PathRef(pathId, List.copyOf(pathChoices)), candidates,
                details);
    }

    /** Các phần kèm phương án đã chọn, theo thứ tự của đường (phục vụ dựng đề). */
    public record ResolvedPart(ExamPart part, ExamPartOption option) {
    }

    /** Thứ tự các phần trên đường đã giải quyết trong {@link #build} (phục vụ dựng đề). */
    public List<ResolvedPart> orderedParts(ExamStructureDocument doc, String pathId) {
        String[] wanted = DEFAULT_PATH_ID.equals(pathId) ? new String[0]
                : pathId.split("\\+", -1);
        int wantedIndex = 0;
        List<ResolvedPart> out = new ArrayList<>();
        for (ExamPart part : sortedParts(doc)) {
            ExamPartOption option = null;
            if (isChoice(part)) {
                if (wantedIndex >= wanted.length) {
                    throw pathNotFound(pathId);
                }
                String optionId = wanted[wantedIndex++];
                option = part.options().stream().filter(o -> optionId.equals(o.id())).findFirst()
                        .orElseThrow(() -> pathNotFound(pathId));
            }
            out.add(new ResolvedPart(part, option));
        }
        if (wantedIndex != wanted.length) {
            throw pathNotFound(pathId);
        }
        return out;
    }

    /**
     * Gắn nhóm treo trực tiếp dưới phương án (không thuộc môn nào) vào môn của phương án SUBJECT;
     * các loại phương án khác gộp vào môn đầu tiên của phương án để nhóm vẫn lấy nguồn được.
     */
    private void attachOptionBlocks(ExamPart part, ExamPartOption option,
            List<SectionOnPath> sections, Map<String, ExamBlockRef> refByBlockId,
            Map<String, ExamKnowledgeScopes.BlockScope> scopeByBlockId,
            Map<UUID, String> itemCodeCache,
            Map<GroupQuota, QuestionSourcePort.Filter> filters,
            Map<String, GroupDetail> details) {
        if (option.blocks().isEmpty()) {
            return;
        }
        String subjectCode = option.kind() == OptionKind.SUBJECT && !option.sections().isEmpty()
                ? option.sections().getFirst().subjectCode()
                : option.sections().isEmpty() ? null : option.sections().getFirst().subjectCode();
        if (subjectCode == null) {
            subjectCode = option.code();
        }
        ExamSection holder = new ExamSection(option.id() + ":shared", subjectCode, null, null,
                null, null, null, null, option.blocks());
        sections.add(sectionOnPath(holder, refByBlockId, scopeByBlockId, itemCodeCache,
                filters, details, part, option.id(), false));
    }

    /** Nhóm và bộ lọc nguồn của một môn; nhớ cặp quota → filter theo groupId. */
    private SectionOnPath sectionOnPath(ExamSection section,
            Map<String, ExamBlockRef> refByBlockId,
            Map<String, ExamKnowledgeScopes.BlockScope> scopeByBlockId,
            Map<UUID, String> itemCodeCache,
            Map<GroupQuota, QuestionSourcePort.Filter> filters,
            Map<String, GroupDetail> details, ExamPart part, String partOptionId,
            boolean subjectChoiceSection) {
        List<GroupQuota> groups = new ArrayList<>();
        for (QuestionBlock block : section.blocks()) {
            GroupQuota quota = groupQuota(section, block);
            ExamBlockRef ref = refByBlockId.get(block.id());
            filters.put(quota, filterFor(section, block, ref, scopeByBlockId.get(block.id()),
                    itemCodeCache));
            details.put(block.id(), new GroupDetail(part.id(), part.name(), part.order(),
                    part.durationMinutes(), partOptionId, section.id(), section.subjectCode(),
                    block, ref));
            groups.add(quota);
        }
        SubjectQuota quota = subjectQuota(section);
        String choiceGroup = section.choiceGroup() == null ? null : section.choiceGroup().name();
        return new SectionOnPath(section.subjectCode(), section.id(),
                subjectChoiceSection ? null : section.questionCount(),
                quota, groups, choiceGroup);
    }

    private GroupQuota groupQuota(ExamSection section, QuestionBlock block) {
        boolean isSet = block.questionType() == QuestionType.QUESTION_SET
                || block.questionType() == QuestionType.CLOZE;
        String expected = expectedScoring(block);
        if (isSet) {
            if (block.setCount() == null || block.setCount() <= 0
                    || block.setSize() == null || block.setSize() <= 0) {
                throw new BusinessRuleException("EXAM_STRUCTURE_INVALID",
                        "Nhóm câu chùm cần setCount và setSize dương");
            }
            return new GroupQuota(block.id(), GroupQuota.GroupKind.SET, null, null,
                    block.setCount(), block.setSize(),
                    answerTypeName(block.childAnswerType()), null, List.of(), List.of(),
                    block.requiredPointsPerQuestion(), expected);
        }
        List<CompetencyQuota> competencies = competencyQuotas(block);
        return new GroupQuota(block.id(), GroupQuota.GroupKind.SINGLE, block.questionCount(),
                answerTypeName(block.questionType()), null, null, null,
                matrixQuota(block.matrix()), competencies, List.of(),
                block.requiredPointsPerQuestion(), expected);
    }

    private QuestionSourcePort.Filter filterFor(ExamSection section, QuestionBlock block,
            ExamBlockRef ref, ExamKnowledgeScopes.BlockScope scope,
            Map<UUID, String> itemCodeCache) {
        UUID subjectId = subjectIdOf(section.subjectCode());
        List<List<UUID>> knowledgeGroups = null;
        QuestionSourcePort.Match match = QuestionSourcePort.Match.ANY;
        if (scope != null && !scope.selectedSubtrees().isEmpty()) {
            knowledgeGroups = scope.selectedSubtrees().stream().map(List::copyOf).toList();
            match = "ALL".equals(scope.match()) ? QuestionSourcePort.Match.ALL
                    : QuestionSourcePort.Match.ANY;
        }
        List<QuestionType> answerTypes = null;
        QuestionType questionType = block.questionType();
        boolean isSet = questionType == QuestionType.QUESTION_SET
                || questionType == QuestionType.CLOZE;
        if (isSet) {
            // khối chùm lọc theo dạng gốc; dạng câu con do childAnswerType ràng
        } else if (questionType == null) {
            answerTypes = answerTypesOf(ref);
        }
        List<String> competencyCodes = competencyCodesOf(section, block, ref, itemCodeCache);
        return new QuestionSourcePort.Filter(subjectId, knowledgeGroups, match, answerTypes,
                questionType, block.taskTypeCode(), block.childAnswerType(), block.tagCodes(),
                competencyCodes, block.requiredPointsPerQuestion());
    }

    private List<QuestionType> answerTypesOf(ExamBlockRef ref) {
        if (ref == null || ref.getAnswerTypes() == null) {
            return null;
        }
        List<QuestionType> out = new ArrayList<>();
        for (String name : ref.getAnswerTypes()) {
            try {
                out.add(QuestionType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // giá trị danh mục lạ: bỏ qua, chỉ tiêu cấu trúc vẫn bắt
            }
        }
        return out.isEmpty() ? null : out;
    }

    private List<String> competencyCodesOf(ExamSection section, QuestionBlock block,
            ExamBlockRef ref, Map<UUID, String> itemCodeCache) {
        if (ref != null && ref.getCompetencyItemIds() != null
                && !ref.getCompetencyItemIds().isEmpty()) {
            return ref.getCompetencyItemIds().stream()
                    .map(id -> itemCodeCache.computeIfAbsent(id,
                            key -> catalog.findItem(key).map(v -> v.code()).orElse(null)))
                    .filter(code -> code != null).toList();
        }
        KnowledgeScope scope = block.knowledgeScope() != null ? block.knowledgeScope()
                : section.knowledgeScope();
        return scope == null || scope.competencyCodes().isEmpty() ? List.of()
                : scope.competencyCodes();
    }

    private SubjectQuota subjectQuota(ExamSection section) {
        SubjectQuota.AnswerQuotas answers = null;
        if (section.answerQuotas() != null && !section.answerQuotas().items().isEmpty()) {
            List<DimensionQuota> items = new ArrayList<>();
            for (var item : section.answerQuotas().items()) {
                if (item.answerType() != null && item.value() != null) {
                    items.add(new DimensionQuota(item.answerType().name(), quotaValue(item.value())));
                }
            }
            answers = new SubjectQuota.AnswerQuotas(items,
                    section.answerQuotas().unit() == null ? QuotaUnit.COUNT
                            : QuotaUnit.valueOf(section.answerQuotas().unit().name()));
        }
        return new SubjectQuota(answers, matrixQuota(section.matrix()));
    }

    private MatrixQuota matrixQuota(BlockMatrix matrix) {
        if (matrix == null) {
            return null;
        }
        QuotaUnit unit = matrix.unit() == null ? QuotaUnit.COUNT
                : QuotaUnit.valueOf(matrix.unit().name());
        if (matrix.mode() != null && matrix.mode().name().equals("COMBINED")) {
            List<CellQuota> cells = new ArrayList<>();
            for (var cell : matrix.cells()) {
                cells.add(new CellQuota(cell.gradeCode(), cell.difficultyCode(),
                        quotaValue(cell.value())));
            }
            return new MatrixQuota.Combined(cells, unit);
        }
        List<DimensionQuota> grades = new ArrayList<>();
        for (var v : matrix.byGrade()) {
            grades.add(new DimensionQuota(v.code(), quotaValue(v.value())));
        }
        List<DimensionQuota> difficulties = new ArrayList<>();
        for (var v : matrix.byDifficulty()) {
            difficulties.add(new DimensionQuota(v.code(), quotaValue(v.value())));
        }
        return new MatrixQuota.Separate(grades, difficulties, unit);
    }

    private List<CompetencyQuota> competencyQuotas(QuestionBlock block) {
        if (block.matrix() == null || block.matrix().byCompetency().isEmpty()) {
            return List.of();
        }
        QuotaUnit unit = block.matrix().unit() == null ? QuotaUnit.COUNT
                : QuotaUnit.valueOf(block.matrix().unit().name());
        List<CompetencyQuota> out = new ArrayList<>();
        for (var v : block.matrix().byCompetency()) {
            out.add(new CompetencyQuota(v.code(), quotaValue(v.value()), unit));
        }
        return out;
    }

    private static String expectedScoring(QuestionBlock block) {
        if (block.expectedScoring() == null) {
            return null;
        }
        if (block.expectedScoring().trueFalseMethod() != null) {
            return block.expectedScoring().trueFalseMethod();
        }
        return block.expectedScoring().multipleChoiceRule();
    }

    private static String answerTypeName(QuestionType type) {
        return type == null ? null : type.name();
    }

    private static int quotaValue(BigDecimal value) {
        try {
            return value == null ? 0 : value.intValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessRuleException("PREVIEW_QUOTA_NOT_INTEGER",
                    "Chỉ tiêu ma trận phải là số nguyên");
        }
    }

    private UUID subjectIdOf(String subjectCode) {
        if (subjectCode == null) {
            return null;
        }
        return catalog.findActiveItem("SUBJECT", subjectCode)
                .map(v -> v.id()).orElse(null);
    }

    private SubjectPick resolvePick(SubjectChoice choice, List<String> subjectCodes, long seed,
            SubjectCountAllocator allocator) {
        if (subjectCodes == null || subjectCodes.size() != choice.choose()
                || subjectCodes.stream().distinct().count() != subjectCodes.size()) {
            throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
        }
        Set<String> wanted = Set.copyOf(subjectCodes);
        List<SectionOnPath> picked = new ArrayList<>(choice.choose());
        for (SectionOnPath s : choice.sections()) {
            if (wanted.contains(s.subjectCode())) {
                picked.add(s);
            }
        }
        if (picked.size() != choice.choose()) {
            throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
        }
        int[] counts = allocator.select(choice, picked, seed);
        List<Integer> countsList = new ArrayList<>(counts.length);
        for (int c : counts) {
            countsList.add(c);
        }
        return new SubjectPick(choice.optionId(), picked, countsList);
    }

    private static int chooseOf(ExamPartOption option) {
        if (option.chooseSubjects() == null || option.chooseSubjects() <= 0) {
            throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
        }
        return option.chooseSubjects();
    }

    private static List<Integer> countsOf(ExamPartOption option) {
        if (option.countsByPick() == null || option.countsByPick().isEmpty()) {
            throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
        }
        return option.countsByPick();
    }

    private static boolean isChoice(ExamPart part) {
        return part.participation() != null
                && part.participation().mode() == ParticipationMode.CHOICE;
    }

    private static List<ExamPart> sortedParts(ExamStructureDocument doc) {
        List<ExamPart> parts = new ArrayList<>(doc == null ? List.of() : doc.parts());
        parts.sort((a, b) -> {
            Integer oa = a.order();
            Integer ob = b.order();
            if (oa == null && ob == null) {
                return 0;
            }
            if (oa == null) {
                return 1;
            }
            if (ob == null) {
                return -1;
            }
            return Integer.compare(oa, ob);
        });
        return parts;
    }

    private static NotFoundException pathNotFound(String pathId) {
        return new NotFoundException("PATH_NOT_FOUND", "Không có đường " + pathId);
    }

    private static PathCandidate toPathCandidate(QuestionSourcePort.Candidate c) {
        List<CountingUnit> units = new ArrayList<>(c.units().size());
        for (QuestionSourcePort.Unit u : c.units()) {
            units.add(new CountingUnit(unitChildId(c, u), u.answerType().name(), u.gradeCode(),
                    u.difficultyCode(),
                    u.mapidIds() == null ? List.of()
                            : u.mapidIds().stream().map(UUID::toString).toList(),
                    u.competencyCodes(), u.points()));
        }
        return new PathCandidate(c.questionId().toString(), c.versionNo(),
                c.setId() == null ? null : c.setId().toString(), c.taskTypeCode(), c.scoringRule(),
                units);
    }

    /** Định danh đơn vị con: câu con của bộ dùng id dòng; vị trí Điền khuyết dùng reference. */
    private static String unitChildId(QuestionSourcePort.Candidate c,
            QuestionSourcePort.Unit u) {
        if (c.setId() == null) {
            return null;
        }
        if (u.questionId() != null && !u.questionId().equals(c.questionId())) {
            return u.questionId().toString();
        }
        return u.reference();
    }
}
