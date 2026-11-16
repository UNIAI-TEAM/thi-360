package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogItemRefView;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.exam.api.CatalogOption;
import vn.unicom.thi360.exam.api.CombinationOption;
import vn.unicom.thi360.exam.api.CompetencyOption;
import vn.unicom.thi360.exam.api.ContentReferenceResponse;
import vn.unicom.thi360.exam.api.ExamKindOption;
import vn.unicom.thi360.exam.api.KnowledgeNode;
import vn.unicom.thi360.exam.api.QuestionTypeOption;
import vn.unicom.thi360.exam.domain.ExamKindProfile;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.web.LocaleConfig;

/**
 * Dựng {@link ContentReferenceResponse}: gộp lựa chọn danh mục (SUBJECT, GRADE, QUESTION_LEVEL,
 * QUESTION_TAG, SUBJECT_COMBINATION, COMPETENCY, KNOWLEDGE, TASK_TYPE), tám dạng câu cố định và
 * hồ sơ kỳ thi (để rỗng tới BE-4).
 *
 * <p>Cache Redis chứa bản song ngữ (nameVi + nameEn) kèm revision
 * danh mục ({@link CatalogLookup#revision()}): mỗi request so revision hiện tại, khớp thì trả
 * bản đã lưu — cách này tự xóa cache khi danh mục đổi mà không cần hook từ module catalog.
 * Khi hồ sơ kỳ thi ra đời (BE-4), cần xóa khóa này hoặc cộng thêm revision hồ sơ vào key.
 */
@Service
public class ContentReferenceService {

    // Khóa có hậu tố phiên bản: đổi hậu tố mỗi khi hình dạng payload hoặc logic dựng cache
    // thay đổi — bản cache của builder cũ không được code mới phục vụ (R2-1).
    public static final String CACHE_KEY = "exam:content-reference:v3";
    private static final long CACHE_TTL_SECONDS = 6 * 3600L;
    private static final String SUBJECT_GROUP = "SUBJECT";
    private static final String EXAM_GROUP = "EXAM";
    private static final String GRADE_GROUP = "GRADE";
    private static final String KNOWLEDGE_GROUP = "KNOWLEDGE";

    private static final Logger log = LoggerFactory.getLogger(ContentReferenceService.class);

    private final CatalogLookup catalogLookup;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExamKindProfileRepository profileRepository;

    public ContentReferenceService(
            CatalogLookup catalogLookup,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ExamKindProfileRepository profileRepository) {
        this.catalogLookup = catalogLookup;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public ContentReferenceResponse getContentReference() {
        long revision = catalogLookup.revision();
        CachedReference cached = readCache();
        if (cached != null && cached.catalogRevision() == revision) {
            return toResponse(cached);
        }
        CachedReference built = build(revision);
        writeCache(built);
        return toResponse(built);
    }

    CachedReference build(long revision) {
        List<NamePair> subjects = namesOf(catalogLookup.listActiveGroupItems(SUBJECT_GROUP));
        List<NamePair> grades = namesOf(catalogLookup.listActiveGroupItems(GRADE_GROUP));
        List<NamePair> difficulties = namesOf(catalogLookup.listActiveGroupItems("QUESTION_LEVEL"));
        List<NamePair> tags = namesOf(catalogLookup.listActiveGroupItems("QUESTION_TAG"));
        List<NamePair> taskTypes = namesOf(catalogLookup.listActiveGroupItems("TASK_TYPE"));

        // Định danh danh mục là code_normalized: mã lưu có thể lẫn hoa/thường nên so theo
        // dạng chuẩn hóa; mã trả về vẫn là enum cố định (D1).
        Map<String, CatalogItemView> formatsByCode = new LinkedHashMap<>();
        for (CatalogItemView item : catalogLookup.listGroupItems("QUESTION_FORMAT")) {
            formatsByCode.putIfAbsent(item.codeNormalized(), item);
        }
        List<CachedQuestionType> questionTypes = new ArrayList<>(QuestionType.values().length);
        for (QuestionType type : QuestionType.values()) {
            CatalogItemView item = formatsByCode.get(type.code().toLowerCase(Locale.ROOT));
            if (item == null) {
                questionTypes.add(new CachedQuestionType(
                        type.code(), type.defaultNameVi(), type.defaultNameEn(), true));
            } else if (item.status() == CatalogStatus.ACTIVE) {
                questionTypes.add(new CachedQuestionType(
                        type.code(), item.nameVi(), item.nameEn(), true));
            } else {
                questionTypes.add(new CachedQuestionType(
                        type.code(), type.defaultNameVi(), type.defaultNameEn(), false));
            }
        }

        List<CachedCombination> combinations = new ArrayList<>();
        for (CatalogCombinationView combo : catalogLookup.listCurrentCombinations("SUBJECT_COMBINATION")) {
            combinations.add(new CachedCombination(
                    new NamePair(combo.itemCode(), combo.itemNameVi(), combo.itemNameEn()),
                    combo.itemCode(),
                    combo.subjectCodes(),
                    combo.examItemCode()));
        }

        List<CatalogItemView> competencyItems = catalogLookup.listActiveGroupItems("COMPETENCY");
        Map<UUID, List<CatalogItemRefView>> competencyRefs =
                catalogLookup.findEffectiveItemRefs(
                        competencyItems.stream().map(CatalogItemView::id).toList());
        List<CachedCompetency> competencies = new ArrayList<>(competencyItems.size());
        for (CatalogItemView item : competencyItems) {
            String subjectCode = null;
            List<String> examCodes = new ArrayList<>();
            for (CatalogItemRefView ref : competencyRefs.getOrDefault(item.id(), List.of())) {
                if (ref.refStatus() != CatalogStatus.ACTIVE) {
                    continue;
                }
                if (SUBJECT_GROUP.equals(ref.refGroupCode())) {
                    subjectCode = ref.refItemCode();
                } else if (EXAM_GROUP.equals(ref.refGroupCode())) {
                    examCodes.add(ref.refItemCode());
                }
            }
            competencies.add(new CachedCompetency(
                    new NamePair(item.code(), item.nameVi(), item.nameEn()),
                    item.code(),
                    subjectCode,
                    List.copyOf(examCodes)));
        }

        List<CatalogItemView> knowledgeItems = catalogLookup.listActiveGroupItems(KNOWLEDGE_GROUP);
        Map<UUID, List<CatalogItemRefView>> knowledgeRefs =
                catalogLookup.findEffectiveItemRefs(
                        knowledgeItems.stream().map(CatalogItemView::id).toList());
        List<CachedKnowledgeNode> knowledgeTree = buildKnowledgeTree(knowledgeItems, knowledgeRefs);

        Map<UUID, ExamKindProfile> profiles = new LinkedHashMap<>();
        profileRepository.findAll().forEach(profile -> profiles.put(profile.getExamItemId(), profile));
        List<CachedExamKind> examKinds = catalogLookup.listActiveGroupItems(EXAM_GROUP).stream()
                .map(item -> {
                    ExamKindProfile p = profiles.get(item.id());
                    return new CachedExamKind(item.id(),
                            new NamePair(item.code(), item.nameVi(), item.nameEn()),
                            p == null ? List.of() : p.getScopes(),
                            p == null ? null : p.getTemplateBy(),
                            p == null ? null : p.getTiming(),
                            p == null ? null : p.getMaxScore(),
                            p == null ? null : p.getReferenceMax(),
                            p == null ? null : p.getPracticeScoring(),
                            p == null ? null : p.getTfScoring(),
                            p == null ? null : p.getMultipleChoiceRule(), p != null);
                }).toList();

        return new CachedReference(
                revision,
                subjects,
                grades,
                difficulties,
                tags,
                examKinds,
                combinations,
                competencies,
                knowledgeTree,
                taskTypes,
                questionTypes);
    }

    /**
     * Cây danh mục KNOWLEDGE có ba cấp (Chương → Bài → Kiến thức); hợp đồng của client lại
     * bắt đầu ở cấp SUBJECT. Các nút cấp Chương có cùng một liên kết SUBJECT hiệu lực được
     * gom lại dưới một nút môn dựng từ giá trị SUBJECT được liên kết (id = id giá trị môn,
     * children = các chương). Chương chưa gắn môn (hoặc gắn môn đã ngừng) giữ nguyên cấp
     * CHAPTER ở gốc — không bịa dữ liệu môn.
     */
    private List<CachedKnowledgeNode> buildKnowledgeTree(
            List<CatalogItemView> items, Map<UUID, List<CatalogItemRefView>> refsByItem) {
        Map<UUID, List<CatalogItemView>> childrenByParent = new LinkedHashMap<>();
        List<CatalogItemView> roots = new ArrayList<>();
        for (CatalogItemView item : items) {
            if (item.parentId() == null) {
                roots.add(item);
            } else {
                childrenByParent.computeIfAbsent(item.parentId(), k -> new ArrayList<>()).add(item);
            }
        }

        Map<UUID, CatalogItemRefView> subjectsById = new LinkedHashMap<>();
        Map<UUID, List<CachedKnowledgeNode>> chaptersBySubject = new LinkedHashMap<>();
        List<CachedKnowledgeNode> ungroupedRoots = new ArrayList<>();
        for (CatalogItemView root : roots) {
            CachedKnowledgeNode node = toNode(root, childrenByParent, refsByItem);
            CatalogItemRefView subjectRef =
                    firstActiveRef(refsByItem.get(root.id()), SUBJECT_GROUP);
            if (subjectRef == null) {
                ungroupedRoots.add(node);
                continue;
            }
            subjectsById.putIfAbsent(subjectRef.refItemId(), subjectRef);
            chaptersBySubject.computeIfAbsent(subjectRef.refItemId(), k -> new ArrayList<>())
                    .add(node);
        }

        List<CachedKnowledgeNode> tree = new ArrayList<>(subjectsById.size() + ungroupedRoots.size());
        for (CatalogItemRefView subject : subjectsById.values()) {
            tree.add(new CachedKnowledgeNode(
                    subject.refItemId(),
                    subject.refItemCode(),
                    new NamePair(subject.refItemCode(), subject.refNameVi(), subject.refNameEn()),
                    KnowledgeNode.KnowledgeLevel.SUBJECT,
                    subject.refItemCode(),
                    null,
                    chaptersBySubject.getOrDefault(subject.refItemId(), List.of())));
        }
        tree.addAll(ungroupedRoots);
        return tree;
    }

    private static CatalogItemRefView firstActiveRef(List<CatalogItemRefView> refs, String groupCode) {
        if (refs == null) {
            return null;
        }
        for (CatalogItemRefView ref : refs) {
            if (ref.refStatus() == CatalogStatus.ACTIVE && groupCode.equals(ref.refGroupCode())) {
                return ref;
            }
        }
        return null;
    }

    private CachedKnowledgeNode toNode(
            CatalogItemView item,
            Map<UUID, List<CatalogItemView>> childrenByParent,
            Map<UUID, List<CatalogItemRefView>> refsByItem) {
        String subjectCode = null;
        String gradeCode = null;
        for (CatalogItemRefView ref : refsByItem.getOrDefault(item.id(), List.of())) {
            if (ref.refStatus() != CatalogStatus.ACTIVE) {
                continue;
            }
            if (SUBJECT_GROUP.equals(ref.refGroupCode()) && subjectCode == null) {
                subjectCode = ref.refItemCode();
            } else if (GRADE_GROUP.equals(ref.refGroupCode()) && gradeCode == null) {
                gradeCode = ref.refItemCode();
            }
        }
        List<CachedKnowledgeNode> children = childrenByParent
                .getOrDefault(item.id(), List.of())
                .stream()
                .map(child -> toNode(child, childrenByParent, refsByItem))
                .toList();
        return new CachedKnowledgeNode(
                item.id(),
                item.code(),
                new NamePair(item.code(), item.nameVi(), item.nameEn()),
                levelOf(item.depth()),
                subjectCode,
                gradeCode,
                children);
    }

    // Danh mục KNOWLEDGE có ba cấp: Chương (1) → Bài (2) → Kiến thức (3 trở xuống); cấp
    // SUBJECT do cây trả ra dựng từ liên kết SUBJECT, không phải một cấp trong danh mục.
    private static KnowledgeNode.KnowledgeLevel levelOf(short depth) {
        return switch (depth) {
            case 1 -> KnowledgeNode.KnowledgeLevel.CHAPTER;
            case 2 -> KnowledgeNode.KnowledgeLevel.LESSON;
            default -> KnowledgeNode.KnowledgeLevel.KNOWLEDGE;
        };
    }

    private static List<NamePair> namesOf(Collection<CatalogItemView> items) {
        return items.stream()
                .map(item -> new NamePair(item.code(), item.nameVi(), item.nameEn()))
                .toList();
    }

    private ContentReferenceResponse toResponse(CachedReference cached) {
        boolean english = Locale.ENGLISH.getLanguage()
                .equalsIgnoreCase(LocaleConfig.currentLocale().getLanguage());
        return new ContentReferenceResponse(
                options(cached.subjects(), english),
                options(cached.grades(), english),
                options(cached.difficulties(), english),
                options(cached.tags(), english),
                cached.examKinds().stream().map(k -> new ExamKindOption(
                        k.itemId(), k.name().code(), k.name().pick(english), k.scopes(),
                        k.templateBy(), k.timing(), k.maxScore(), k.referenceMax(),
                        k.practiceScoring(), k.trueFalseScoring(), k.multipleChoiceRule(),
                        k.profileSet())).toList(),
                cached.combinations().stream()
                        .map(c -> new CombinationOption(
                                c.code(), c.name().pick(english), c.subjectCodes(), c.examKindCode()))
                        .toList(),
                cached.competencies().stream()
                        .map(c -> new CompetencyOption(
                                c.code(), c.name().pick(english), c.subjectCode(), c.examKindCodes()))
                        .toList(),
                cached.knowledgeTree().stream()
                        .map(node -> toKnowledgeNode(node, english))
                        .toList(),
                options(cached.taskTypes(), english),
                cached.questionTypes().stream()
                        .map(t -> new QuestionTypeOption(
                                t.code(), pick(t.nameVi(), t.nameEn(), english), t.active()))
                        .toList());
    }

    private KnowledgeNode toKnowledgeNode(CachedKnowledgeNode node, boolean english) {
        return new KnowledgeNode(
                node.id(),
                node.code(),
                node.name().pick(english),
                node.level(),
                node.subjectCode(),
                node.gradeCode(),
                true,
                node.children().stream().map(child -> toKnowledgeNode(child, english)).toList());
    }

    private static List<CatalogOption> options(List<NamePair> names, boolean english) {
        return names.stream().map(n -> new CatalogOption(n.code(), n.pick(english))).toList();
    }

    private static String pick(String nameVi, String nameEn, boolean english) {
        if (english && nameEn != null && !nameEn.isBlank()) {
            return nameEn;
        }
        return nameVi;
    }

    private CachedReference readCache() {
        try {
            String raw = redisTemplate.opsForValue().get(CACHE_KEY);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, CachedReference.class);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn().setCause(ex).log("Failed to read exam content-reference cache; rebuilding");
            return null;
        }
    }

    private void writeCache(CachedReference built) {
        try {
            redisTemplate.opsForValue()
                    .set(CACHE_KEY, objectMapper.writeValueAsString(built), CACHE_TTL_SECONDS,
                            java.util.concurrent.TimeUnit.SECONDS);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.atWarn().setCause(ex).log("Failed to write exam content-reference cache");
        }
    }

    public void evictCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Failed to evict exam content-reference cache");
        }
    }

    public record NamePair(String code, String vi, String en) {
        String pick(boolean english) {
            return ContentReferenceService.pick(vi, en, english);
        }
    }

    public record CachedReference(
            long catalogRevision,
            List<NamePair> subjects,
            List<NamePair> grades,
            List<NamePair> difficulties,
            List<NamePair> tags,
            List<CachedExamKind> examKinds,
            List<CachedCombination> combinations,
            List<CachedCompetency> competencies,
            List<CachedKnowledgeNode> knowledgeTree,
            List<NamePair> taskTypes,
            List<CachedQuestionType> questionTypes
    ) {
    }

    public record CachedExamKind(UUID itemId, NamePair name, List<String> scopes,
            String templateBy, String timing, BigDecimal maxScore, BigDecimal referenceMax,
            String practiceScoring, JsonNode trueFalseScoring, String multipleChoiceRule,
            boolean profileSet) {
    }

    public record CachedCombination(
            NamePair name, String code, List<String> subjectCodes, String examKindCode) {
    }

    public record CachedCompetency(
            NamePair name, String code, String subjectCode, List<String> examKindCodes) {
    }

    public record CachedKnowledgeNode(
            UUID id,
            String code,
            NamePair name,
            KnowledgeNode.KnowledgeLevel level,
            String subjectCode,
            String gradeCode,
            List<CachedKnowledgeNode> children
    ) {
    }

    public record CachedQuestionType(String code, String nameVi, String nameEn, boolean active) {
    }
}
