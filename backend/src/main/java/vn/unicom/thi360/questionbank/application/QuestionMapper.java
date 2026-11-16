package vn.unicom.thi360.questionbank.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.KnowledgeRef;
import vn.unicom.thi360.questionbank.api.PersonRef;
import vn.unicom.thi360.questionbank.api.QuestionDetail;
import vn.unicom.thi360.questionbank.api.QuestionSummary;
import vn.unicom.thi360.questionbank.api.QuestionUsage;
import vn.unicom.thi360.questionbank.api.QuestionVersionItem;
import vn.unicom.thi360.questionbank.application.QuestionSearchDao.QuestionRow;
import vn.unicom.thi360.questionbank.domain.Question;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionKnowledge;
import vn.unicom.thi360.questionbank.domain.QuestionSearchNormalizer;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;
import vn.unicom.thi360.questionbank.domain.QuestionStatus;
import vn.unicom.thi360.questionbank.domain.QuestionTag;
import vn.unicom.thi360.questionbank.domain.QuestionUsageLedger;
import vn.unicom.thi360.questionbank.domain.QuestionVersion;
import vn.unicom.thi360.questionbank.infrastructure.QuestionKnowledgeRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionTagRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionUsageLedgerRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionVersionRepository;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

/**
 * Ánh xạ bản ghi/bản chụp câu hỏi sang DTO trả ra (spec 9.2). Giá trị danh mục trả mã
 * ({@code subjectCode}…), MAPID trả {@code nodeId/code/name/primary}, người dùng tra qua
 * {@link UserDirectory}. {@link #toDetail} đọc bản chụp phiên bản của chính câu; câu con của
 * bộ lồng đệ quy (bản chụp câu con cùng {@code version_no} của bộ).
 */
@Component
public class QuestionMapper {

    private static final int PREVIEW_LENGTH = 200;

    private final CatalogLookup catalogLookup;
    private final UserDirectory userDirectory;
    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionKnowledgeRepository knowledgeRepository;
    private final QuestionTagRepository tagRepository;
    private final QuestionUsageLedgerRepository ledgerRepository;
    private final QuestionUsageProvider usageProvider;

    public QuestionMapper(
            CatalogLookup catalogLookup,
            UserDirectory userDirectory,
            QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository,
            QuestionKnowledgeRepository knowledgeRepository,
            QuestionTagRepository tagRepository,
            QuestionUsageLedgerRepository ledgerRepository,
            QuestionUsageProvider usageProvider) {
        this.catalogLookup = catalogLookup;
        this.userDirectory = userDirectory;
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.tagRepository = tagRepository;
        this.ledgerRepository = ledgerRepository;
        this.usageProvider = usageProvider;
    }

    /** Bộ đệm giá trị danh mục + người dùng cho một lô (một trang danh sách hoặc một cây bộ). */
    public class Batch {
        private final Map<UUID, CatalogItemView> items = new HashMap<>();
        private final Map<UUID, UserDirectory.UserSummary> users = new HashMap<>();
        private final Map<UUID, List<QuestionKnowledge>> knowledge = new HashMap<>();
        private final Map<UUID, List<UUID>> tags = new HashMap<>();

        private CatalogItemView item(UUID id) {
            if (id == null) {
                return null;
            }
            return items.computeIfAbsent(id, k -> catalogLookup.findItem(k).orElse(null));
        }

        private UserDirectory.UserSummary user(UUID id) {
            if (id == null) {
                return null;
            }
            return users.computeIfAbsent(id, k -> userDirectory.findById(k).orElse(null));
        }

        private List<QuestionKnowledge> knowledgeOf(UUID questionId) {
            return knowledge.computeIfAbsent(questionId, knowledgeRepository::findByQuestionId);
        }

        private List<UUID> tagsOf(UUID questionId) {
            return tags.computeIfAbsent(questionId,
                    q -> tagRepository.findByQuestionId(q).stream()
                            .map(QuestionTag::getTagItemId).toList());
        }
    }

    public Batch batch() {
        return new Batch();
    }

    /** Tải sẵn phân loại của nhiều câu (tránh N+1 khi map trang danh sách). */
    public Batch batch(Collection<UUID> questionIds) {
        Batch batch = batch();
        if (questionIds == null || questionIds.isEmpty()) {
            return batch;
        }
        for (QuestionKnowledge k : knowledgeRepository.findByQuestionIdIn(questionIds)) {
            batch.knowledge.computeIfAbsent(k.getQuestionId(), q -> new ArrayList<>()).add(k);
        }
        for (QuestionTag t : tagRepository.findByQuestionIdIn(questionIds)) {
            batch.tags.computeIfAbsent(t.getQuestionId(), q -> new ArrayList<>()).add(t.getTagItemId());
        }
        return batch;
    }

    // ---------------------------------------------------------------- tóm tắt

    public QuestionSummary toSummary(Question question, QuestionVersion displayed, Batch batch) {
        List<QuestionKnowledge> knowledge = batch.knowledgeOf(question.getId());
        return new QuestionSummary(
                question.getId(),
                question.getCode(),
                question.getType(),
                question.getStatus(),
                question.getParentId(),
                preview(question.getContentText()),
                codeOf(batch.item(question.getSubjectItemId())),
                codeOf(batch.item(question.getGradeItemId())),
                codeOf(batch.item(question.getLevelItemId())),
                codeOf(batch.item(question.getTaskTypeItemId())),
                codeOf(batch.item(question.getCompetencyItemId())),
                question.getPoints(),
                knowledgeRefs(knowledge, batch),
                tagCodes(batch.tagsOf(question.getId()), batch),
                question.isHasSolution(),
                question.getCurrentVersionNo() == null ? 0 : question.getCurrentVersionNo(),
                question.getWorkingVersionNo(),
                ledgerRepository.countDistinctExamsByQuestionId(question.getId()),
                question.getSourceCode(),
                question.getRevision(),
                personOf(question.getCreatedBy(), batch),
                question.getCreatedAt(),
                question.getUpdatedAt());
    }

    public QuestionSummary toSummary(QuestionRow row, Map<UUID, Question> entities, Batch batch) {
        // entities chứa bản ghi JPA tương ứng (khi cần thuộc tính ngoài truy vấn).
        Question question = entities == null ? null : entities.get(row.id());
        if (question != null) {
            QuestionVersion displayed = displayedVersion(question);
            return toSummary(question, displayed, batch);
        }
        List<QuestionKnowledge> knowledge = batch.knowledgeOf(row.id());
        return new QuestionSummary(
                row.id(),
                row.code(),
                vn.unicom.thi360.questionbank.spi.QuestionType.valueOf(row.type()),
                QuestionStatus.valueOf(row.status()),
                null,
                preview(row.contentText()),
                codeOf(batch.item(row.subjectItemId())),
                codeOf(batch.item(row.gradeItemId())),
                codeOf(batch.item(row.levelItemId())),
                codeOf(batch.item(row.taskTypeItemId())),
                codeOf(batch.item(row.competencyItemId())),
                row.points(),
                knowledgeRefs(knowledge, batch),
                tagCodes(batch.tagsOf(row.id()), batch),
                row.hasSolution(),
                row.currentVersionNo() == null ? 0 : row.currentVersionNo(),
                row.workingVersionNo(),
                ledgerRepository.countDistinctExamsByQuestionId(row.id()),
                row.sourceCode(),
                row.revision(),
                personOf(row.createdBy(), batch),
                row.createdAt(),
                row.updatedAt());
    }

    // ---------------------------------------------------------------- chi tiết

    /** Chi tiết câu: bản chụp của phiên bản đang hiển thị (working nếu có, không thì current). */
    public QuestionDetail toDetail(Question question, Batch batch) {
        QuestionVersion version = displayedVersion(question);
        return toDetail(question, version, batch);
    }

    private QuestionDetail toDetail(Question question, QuestionVersion version, Batch batch) {
        QuestionSnapshot snapshot = version == null ? null : version.getSnapshot();
        QuestionSummary summary = snapshot == null
                ? toSummary(question, version, batch) : snapshotSummary(question, snapshot, batch);
        List<QuestionDetail> children = List.of();
        if (snapshot != null && snapshot.body() instanceof QuestionBody.QuestionSetBody set) {
            children = new ArrayList<>(set.children().size());
            for (int i = 0; i < set.children().size(); i++) {
                QuestionBody.ChildQuestion child = set.children().get(i);
                UUID childId = child.id();
                if (childId == null) {
                    continue;
                }
                Question row = questionRepository.findById(childId).orElseGet(() ->
                        Question.create(childId, question.getCode() + ".draft", child.type(),
                                question.getId(), null, question.getCreatedAt(), question.getCreatedBy()));
                List<UUID> ids = child.knowledgeItemIds() == null
                        ? snapshot.knowledge().stream().map(QuestionSnapshot.KnowledgeRef::itemId).toList()
                        : child.knowledgeItemIds();
                QuestionSnapshot childSnapshot = new QuestionSnapshot(snapshot.subjectItemId(),
                        child.gradeItemId() == null ? snapshot.gradeItemId() : child.gradeItemId(),
                        child.levelItemId() == null ? snapshot.levelItemId() : child.levelItemId(),
                        snapshot.taskTypeItemId(), snapshot.competencyItemId(),
                        child.points() == null ? BigDecimal.ONE : child.points(),
                        ids.stream().map(id -> new QuestionSnapshot.KnowledgeRef(id, false)).toList(),
                        List.of(), child.content(), child.solution(), List.of(), List.of(), child.body(), null);
                QuestionVersion childVersion = new QuestionVersion(childId, version.getVersionNo(),
                        childSnapshot, version.getSavedBy(), version.getSavedAt());
                children.add(toDetail(row, childVersion, batch));
            }
        }
        return new QuestionDetail(
                summary,
                snapshot == null ? null : snapshot.content(),
                snapshot == null ? null : snapshot.solution(),
                snapshot == null ? List.of() : snapshot.commonMistakes(),
                snapshot == null ? List.of() : snapshot.attachmentIds(),
                snapshot == null ? null : snapshot.body(),
                children,
                snapshot,
                version == null ? null : version.getVersionNo(),
                question.getWorkingVersionNo() != null);
    }

    private QuestionSummary snapshotSummary(Question question, QuestionSnapshot snapshot, Batch batch) {
        List<KnowledgeRef> knowledge = snapshot.knowledge().stream().map(k -> {
            CatalogItemView item = batch.item(k.itemId());
            return new KnowledgeRef(k.itemId(), item == null ? null : item.code(),
                    item == null ? null : item.nameVi(), k.primary());
        }).toList();
        return new QuestionSummary(question.getId(), question.getCode(), question.getType(),
                question.getStatus(), question.getParentId(),
                preview(QuestionSearchNormalizer.contentText(snapshot.content(), snapshot.body())),
                codeOf(batch.item(snapshot.subjectItemId())), codeOf(batch.item(snapshot.gradeItemId())),
                codeOf(batch.item(snapshot.levelItemId())), codeOf(batch.item(snapshot.taskTypeItemId())),
                codeOf(batch.item(snapshot.competencyItemId())), snapshot.points(), knowledge,
                tagCodes(snapshot.tagItemIds(), batch), snapshot.solution() != null && !snapshot.solution().isBlank(),
                question.getCurrentVersionNo() == null ? 0 : question.getCurrentVersionNo(),
                question.getWorkingVersionNo(), ledgerRepository.countDistinctExamsByQuestionId(question.getId()),
                snapshot.sourceCode() == null ? question.getSourceCode() : snapshot.sourceCode(),
                question.getRevision(), personOf(question.getCreatedBy(), batch),
                question.getCreatedAt(), question.getUpdatedAt());
    }

    /** Phiên bản đang hiển thị của câu (working nếu có, không current, không thì mới nhất). */
    public QuestionVersion displayedVersion(Question question) {
        Integer no = question.getWorkingVersionNo() != null
                ? question.getWorkingVersionNo()
                : question.getCurrentVersionNo();
        if (no == null) {
            Integer max = versionRepository.findMaxVersionNo(question.getId());
            if (max == null) {
                return null;
            }
            no = max;
        }
        return versionOf(question.getId(), no);
    }

    private QuestionVersion versionOf(UUID questionId, Integer versionNo) {
        if (versionNo == null) {
            return null;
        }
        return versionRepository.findByQuestionIdAndVersionNo(questionId, versionNo).orElse(null);
    }

    // ---------------------------------------------------------------- lịch sử & chỗ dùng

    public QuestionVersionItem toVersionItem(Question question, QuestionVersion version, Batch batch) {
        String state;
        if (Objects.equals(version.getVersionNo(), question.getWorkingVersionNo())) {
            state = "WORKING";
        } else if (Objects.equals(version.getVersionNo(), question.getCurrentVersionNo())) {
            state = question.getStatus() == QuestionStatus.RETIRED ? "RETIRED" : "CURRENT";
        } else {
            state = "SUPERSEDED";
        }
        return new QuestionVersionItem(
                version.getVersionNo(),
                state,
                version.getSavedAt(),
                personOf(version.getSavedBy(), batch),
                version.getReadyAt(),
                version.getSnapshot());
    }

    public List<QuestionUsage.ExamSourceRef> sourceBlocksOf(UUID questionId) {
        return usageProvider.examUsagesOf(questionId).stream()
                .map(ref -> new QuestionUsage.ExamSourceRef(ref.examId()))
                .toList();
    }

    public List<QuestionUsage.LedgerRef> ledgerOf(UUID questionId) {
        return ledgerRepository.findByQuestionIdOrderByFirstAtDesc(questionId).stream()
                .map(QuestionMapper::toLedgerRef)
                .toList();
    }

    private static QuestionUsage.LedgerRef toLedgerRef(QuestionUsageLedger row) {
        return new QuestionUsage.LedgerRef(
                row.getKind().name(), row.getExamId(), row.getVersionNo(), row.getFirstAt());
    }

    // ---------------------------------------------------------------- trợ giúp

    private List<KnowledgeRef> knowledgeRefs(List<QuestionKnowledge> rows, Batch batch) {
        return rows.stream()
                .map(k -> {
                    CatalogItemView item = batch.item(k.getKnowledgeItemId());
                    return new KnowledgeRef(
                            k.getKnowledgeItemId(),
                            item == null ? null : item.code(),
                            item == null ? null : item.nameVi(),
                            k.isPrimary());
                })
                .toList();
    }

    private List<String> tagCodes(List<UUID> tagItemIds, Batch batch) {
        return tagItemIds.stream().map(id -> codeOf(batch.item(id))).filter(Objects::nonNull).toList();
    }

    private PersonRef personOf(UUID userId, Batch batch) {
        if (userId == null) {
            return null;
        }
        UserDirectory.UserSummary user = batch.user(userId);
        return new PersonRef(userId, user == null ? "" : user.displayName());
    }

    private static String codeOf(CatalogItemView item) {
        return item == null ? null : item.code();
    }

    private static String preview(String contentText) {
        if (contentText == null) {
            return "";
        }
        return contentText.length() <= PREVIEW_LENGTH
                ? contentText
                : contentText.substring(0, PREVIEW_LENGTH);
    }
}
