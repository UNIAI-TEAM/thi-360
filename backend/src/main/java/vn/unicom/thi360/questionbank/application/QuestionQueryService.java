package vn.unicom.thi360.questionbank.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.PersonRef;
import vn.unicom.thi360.questionbank.api.QuestionDetail;
import vn.unicom.thi360.questionbank.api.QuestionReadiness;
import vn.unicom.thi360.questionbank.api.QuestionRetireImpact;
import vn.unicom.thi360.questionbank.api.QuestionStatusCounts;
import vn.unicom.thi360.questionbank.api.QuestionSummary;
import vn.unicom.thi360.questionbank.api.QuestionUsage;
import vn.unicom.thi360.questionbank.api.QuestionVersionItem;
import vn.unicom.thi360.questionbank.api.QuestionVersionList;
import vn.unicom.thi360.questionbank.application.QuestionSearchDao.QuestionFilter;
import vn.unicom.thi360.questionbank.application.QuestionSearchDao.QuestionRow;
import vn.unicom.thi360.questionbank.domain.Question;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator;
import vn.unicom.thi360.questionbank.domain.QuestionFieldError;
import vn.unicom.thi360.questionbank.domain.QuestionInput;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;
import vn.unicom.thi360.questionbank.domain.QuestionStatus;
import vn.unicom.thi360.questionbank.domain.QuestionVersion;
import vn.unicom.thi360.questionbank.infrastructure.QuestionRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionVersionRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;

/** Truy vấn của ngân hàng câu hỏi (spec 4.5): danh sách/lọc, chi tiết, điều kiện Sẵn sàng,
 * lịch sử phiên bản, nơi đang dùng, ảnh hưởng khi ngừng và các bộ đếm. */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionSearchDao searchDao;
    private final QuestionSupportDao supportDao;
    private final CatalogLookup catalogLookup;
    private final UserDirectory userDirectory;
    private final QuestionAssetResolver assetResolver;
    private final QuestionMapper mapper;

    public QuestionQueryService(
            QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository,
            QuestionSearchDao searchDao,
            QuestionSupportDao supportDao,
            CatalogLookup catalogLookup,
            UserDirectory userDirectory,
            QuestionAssetResolver assetResolver,
            QuestionMapper mapper) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.searchDao = searchDao;
        this.supportDao = supportDao;
        this.catalogLookup = catalogLookup;
        this.userDirectory = userDirectory;
        this.assetResolver = assetResolver;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- danh sách

    /**
     * Trang danh sách câu độc lập. Tham số lọc theo spec 4.5: từ khóa bỏ dấu trên
     * {@code search_text} (dưới 2 ký tự chỉ so mã), mã môn/khối/độ khó/dạng nhiệm vụ/năng
     * lực, dạng câu, trạng thái, MAPID (cả nút con, D23), nhãn, người tạo, ngày tạo, có
     * lời giải.
     */
    public PageResponse<QuestionSummary> list(
            String keyword,
            String subjectCode,
            String gradeCode,
            String difficultyCode,
            String type,
            String taskTypeCode,
            String competencyCode,
            List<UUID> knowledgeIds,
            List<String> tagCodes,
            QuestionStatus status,
            UUID createdBy,
            LocalDate createdFrom,
            LocalDate createdTo,
            Boolean hasSolution,
            Integer page,
            Integer size,
            String sort) {
        QuestionFilter filter = new QuestionFilter(
                keyword,
                itemIdOrNull("SUBJECT", subjectCode),
                itemIdOrNull("GRADE", gradeCode),
                itemIdOrNull("QUESTION_LEVEL", difficultyCode),
                type,
                itemIdOrNull("TASK_TYPE", taskTypeCode),
                expandKnowledgeIds(knowledgeIds),
                tagIds(tagCodes),
                status, createdBy, createdFrom, createdTo, hasSolution,
                itemIdOrNull("COMPETENCY", competencyCode));
        PageQuery pageQuery = PageQuery.of(
                page, size, sort, QuestionSearchDao.ALLOWED_SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<QuestionRow> rows = searchDao.search(filter, pageQuery);
        QuestionMapper.Batch batch = mapper.batch(rows.items().stream().map(QuestionRow::id).toList());
        List<QuestionSummary> items = rows.items().stream()
                .map(row -> mapper.toSummary(row, null, batch))
                .toList();
        return new PageResponse<>(items, rows.page(), rows.size(), rows.totalItems(), rows.totalPages());
    }

    /** Đếm theo trạng thái với cùng bộ lọc danh sách (bỏ qua tham số trạng thái). */
    public QuestionStatusCounts statusCounts(
            String keyword,
            String subjectCode,
            String gradeCode,
            String difficultyCode,
            String type,
            String taskTypeCode,
            String competencyCode,
            List<UUID> knowledgeIds,
            List<String> tagCodes,
            UUID createdBy,
            LocalDate createdFrom,
            LocalDate createdTo,
            Boolean hasSolution) {
        QuestionFilter filter = new QuestionFilter(
                keyword,
                itemIdOrNull("SUBJECT", subjectCode),
                itemIdOrNull("GRADE", gradeCode),
                itemIdOrNull("QUESTION_LEVEL", difficultyCode),
                type,
                itemIdOrNull("TASK_TYPE", taskTypeCode),
                expandKnowledgeIds(knowledgeIds),
                tagIds(tagCodes),
                null, createdBy, createdFrom, createdTo, hasSolution,
                itemIdOrNull("COMPETENCY", competencyCode));
        Map<QuestionStatus, Long> counts = searchDao.statusCounts(filter);
        return new QuestionStatusCounts(
                counts.getOrDefault(QuestionStatus.DRAFT, 0L),
                counts.getOrDefault(QuestionStatus.READY, 0L),
                counts.getOrDefault(QuestionStatus.RETIRED, 0L));
    }

    /** Người tạo đang có câu (mục lọc "Người tạo" của web admin), sắp theo tên hiển thị. */
    public List<PersonRef> authors() {
        List<UUID> ids = questionRepository.findDistinctCreators();
        Map<UUID, UserDirectory.UserSummary> users = userDirectory.findByIds(ids);
        List<PersonRef> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            UserDirectory.UserSummary user = users.get(id);
            if (user != null) {
                result.add(new PersonRef(id, user.displayName()));
            }
        }
        result.sort(Comparator.comparing(PersonRef::name));
        return result;
    }


    /** Chi tiết câu (độc lập hoặc câu con); câu đã xóa mềm → 404. */
    public QuestionDetail detail(UUID id) {
        Question question = load(id);
        return mapper.toDetail(question, mapper.batch());
    }
    /** Điều kiện Sẵn sàng của phiên bản đang hiển thị (spec 4.5): lỗi chặn + cảnh báo. */
    public QuestionReadiness readiness(UUID id) {
        Question question = load(id);
        QuestionVersion displayed = mapper.displayedVersion(question);
        QuestionSnapshot snapshot = displayed == null ? null : displayed.getSnapshot();
        if (snapshot == null) {
            return new QuestionReadiness(false,
                    List.of(new QuestionFieldError("QUESTION_NOT_READY", "snapshot", "QUESTION_NOT_READY")), List.of());
        }
        QuestionInput input = new QuestionInput(
                question.getType(), snapshot.body(), snapshot.points(),
                snapshot.content(), snapshot.solution(), snapshot.commonMistakes(),
                snapshot.attachmentIds());
        var classification = new QuestionBodyValidator.ClassificationRequirement(
                snapshot.subjectItemId() != null,
                snapshot.gradeItemId() != null,
                snapshot.levelItemId() != null);
        boolean hasPrimary = snapshot.knowledge().stream()
                .anyMatch(QuestionSnapshot.KnowledgeRef::primary);
        var result = QuestionBodyValidator.validateReady(
                input, classification, hasPrimary, assetResolver);
        List<QuestionFieldError> errors = new ArrayList<>(
                QuestionService.childOverrideErrors(snapshot.body(), catalogLookup));
        errors.addAll(result.errors());
        return new QuestionReadiness(errors.isEmpty(), errors, result.warnings());
    }

    /** Lịch sử phiên bản, mới nhất trước (spec 4.5). */
    public QuestionVersionList versions(UUID id) {
        Question question = load(id);
        QuestionMapper.Batch batch = mapper.batch();
        List<QuestionVersionItem> items = versionRepository
                .findByQuestionIdOrderByVersionNo(id).stream()
                .sorted(Comparator.comparing(QuestionVersion::getVersionNo).reversed())
                .map(v -> mapper.toVersionItem(question, v, batch))
                .toList();
        return new QuestionVersionList(items);
    }

    /** Nơi đang dùng câu: khối bài thi qua SPI và dòng sổ đã dùng. */
    public QuestionUsage usage(UUID id) {
        Question question = load(id);
        return new QuestionUsage(mapper.sourceBlocksOf(question.getId()), mapper.ledgerOf(question.getId()));
    }

    /** Ảnh hưởng khi ngừng câu: cùng dữ liệu nơi đang dùng (spec 4.5). */
    public QuestionRetireImpact retireImpact(UUID id) {
        Question question = load(id);
        return new QuestionRetireImpact(
                mapper.sourceBlocksOf(question.getId()), mapper.ledgerOf(question.getId()));
    }

    /** Revision nguồn của các môn (phục vụ màn nguồn; môn chưa đổi trả 0). */
    public Map<UUID, Long> sourceRevisions(List<UUID> subjectItemIds) {
        return supportDao.sourceRevisions(subjectItemIds);
    }

    // ---------------------------------------------------------------- trợ giúp

    private Question load(UUID id) {
        Question question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return question;
    }

    private UUID itemIdOrNull(String groupCode, String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return null;
        }
        // Mã không có: lọc theo id giả để trả trang rỗng thay vì lỗi (lọc là tùy chọn).
        return catalogLookup.findActiveItem(groupCode, itemCode.trim())
                .map(CatalogItemView::id)
                .orElse(new UUID(0L, 0L));
    }

    /** MAPID: nút được chọn và mọi nút con, kể cả nút trung gian (D23). */
    private List<UUID> expandKnowledgeIds(List<UUID> knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return null;
        }
        List<CatalogItemView> nodes = catalogLookup.listGroupItems("KNOWLEDGE");
        List<UUID> expanded = new ArrayList<>(knowledgeIds.size() * 4);
        for (UUID id : knowledgeIds) {
            if (id == null) {
                continue;
            }
            expanded.add(id);
            nodes.stream().filter(node -> node.id().equals(id)).findFirst()
                    .map(CatalogItemView::path)
                    .ifPresent(path -> nodes.stream()
                            .filter(node -> node.path() != null && node.path().startsWith(path))
                            .map(CatalogItemView::id)
                            .filter(descendant -> !descendant.equals(id))
                            .forEach(expanded::add));
        }
        return expanded;
    }

    private List<UUID> tagIds(List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return null;
        }
        List<UUID> ids = new ArrayList<>(tagCodes.size());
        for (String code : tagCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            catalogLookup.findActiveItem("QUESTION_TAG", code.trim())
                    .map(CatalogItemView::id)
                    .ifPresent(ids::add);
        }
        return ids;
    }
}
