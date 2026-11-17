package vn.unicom.thi360.questionbank.application;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusResult;
import vn.unicom.thi360.questionbank.api.QuestionDetail;
import vn.unicom.thi360.questionbank.api.QuestionSummary;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.api.UpdateQuestionRequest;
import vn.unicom.thi360.questionbank.api.UpdateQuestionResponse;
import vn.unicom.thi360.questionbank.domain.Question;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator;
import vn.unicom.thi360.questionbank.domain.QuestionAssetRef;
import vn.unicom.thi360.questionbank.domain.QuestionAssetRefCollector;
import vn.unicom.thi360.questionbank.domain.QuestionFieldError;
import vn.unicom.thi360.questionbank.spi.HtmlContentSanitizer;
import vn.unicom.thi360.questionbank.domain.QuestionInput;
import vn.unicom.thi360.questionbank.domain.QuestionKnowledge;
import vn.unicom.thi360.questionbank.domain.QuestionSearchNormalizer;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;
import vn.unicom.thi360.questionbank.domain.QuestionStatus;
import vn.unicom.thi360.questionbank.domain.QuestionTag;
import vn.unicom.thi360.questionbank.domain.QuestionVersion;
import vn.unicom.thi360.questionbank.infrastructure.QuestionKnowledgeRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionTagRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionUsageLedgerRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionVersionRepository;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRefRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Lệnh ghi của ngân hàng câu hỏi (spec 4.4, 4.5): tạo/sửa (khóa lạc quan qua
 * {@code expectedRevision}, D13), chuyển trạng thái một/hàng loạt, nhân đôi, xóa mềm và
 * khôi phục phiên bản. Mỗi lần lưu ghi một {@code question_version} mới cho câu (và cho từng
 * câu con của bộ, cùng {@code version_no}); câu đã Sẵn sàng hoặc đã Ngừng lưu sang phiên bản
 * làm việc mới và về trạng thái Nháp.
 *
 * <p>Thay đổi tập câu Sẵn sàng của một môn tăng {@code question_source_revision} trong cùng
 * transaction (spec 4.1, D14): sửa câu đang READY, đưa lên READY, ngừng câu READY, xóa câu
 * READY. Mọi lệnh ghi ghi nhật ký {@code QUESTION.*} và chạy trong idempotency theo 7.1.
 */
@Service
public class QuestionService {

    private static final String MODULE = "QUESTION";
    private static final String TARGET = "QUESTION";

    private static final String ERR_REVISION = "QUESTION_REVISION_CONFLICT";
    private static final String ERR_TYPE_IMMUTABLE = "QUESTION_TYPE_IMMUTABLE";
    private static final String ERR_UNSAFE = "QUESTION_CONTENT_UNSAFE";
    private static final String ERR_BODY_INVALID = "QUESTION_BODY_INVALID";
    private static final String ERR_NOT_READY = "QUESTION_NOT_READY";
    private static final String ERR_ALREADY_READY = "QUESTION_ALREADY_READY";
    private static final String ERR_ALREADY_RETIRED = "QUESTION_ALREADY_RETIRED";
    private static final String ERR_IN_USE = "QUESTION_IN_USE";
    private static final String ERR_VERSION_NOT_FOUND = "QUESTION_VERSION_NOT_FOUND";
    private static final String ERR_DUPLICATE_ID = "QUESTION_DUPLICATE_ID";
    private static final String ERR_CATALOG_UNKNOWN = "CATALOG_VALUE_UNKNOWN";

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionKnowledgeRepository knowledgeRepository;
    private final QuestionTagRepository tagRepository;
    private final QuestionUsageLedgerRepository ledgerRepository;
    private final QuestionAssetRefRepository assetRefRepository;
    private final QuestionSupportDao supportDao;
    private final HtmlContentSanitizer sanitizer;
    private final QuestionAssetResolver assetResolver;
    private final CatalogLookup catalogLookup;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotentCommand idempotentCommand;
    private final QuestionMapper mapper;
    private final AppMessages messages;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public QuestionService(
            QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository,
            QuestionKnowledgeRepository knowledgeRepository,
            QuestionTagRepository tagRepository,
            QuestionUsageLedgerRepository ledgerRepository,
            QuestionSupportDao supportDao,
            QuestionAssetRefRepository assetRefRepository,
            HtmlContentSanitizer sanitizer,
            QuestionAssetResolver assetResolver,
            CatalogLookup catalogLookup,
            AuditRecorder auditRecorder,
            CurrentUserProvider currentUserProvider,
            IdempotentCommand idempotentCommand,
            QuestionMapper mapper,
            AppMessages messages,
            EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.tagRepository = tagRepository;
        this.ledgerRepository = ledgerRepository;
        this.supportDao = supportDao;
        this.assetRefRepository = assetRefRepository;
        this.sanitizer = sanitizer;
        this.assetResolver = assetResolver;
        this.catalogLookup = catalogLookup;
        this.auditRecorder = auditRecorder;
        this.currentUserProvider = currentUserProvider;
        this.idempotentCommand = idempotentCommand;
        this.mapper = mapper;
        this.messages = messages;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- tạo / sửa

    /** Tạo câu mới ở trạng thái Nháp, phiên bản làm việc số 1 (spec 4.5). */
    @Transactional
    public QuestionDetail create(String idempotencyKey, SaveQuestionRequest request) {
        return idempotentCommand.execute(
                idempotencyKey, "question.create", request, QuestionDetail.class,
                () -> doCreate(request));
    }

    private QuestionDetail doCreate(SaveQuestionRequest request) {
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        Instant now = Instant.now();
        if (request == null || request.type() == null) {
            throw new BusinessRuleException(ERR_BODY_INVALID, null,
                    List.of(new FieldError("type", "QUESTION_TYPE_MISSING", null)));
        }
        Question question = Question.create(
                UUID.randomUUID(), supportDao.nextQuestionCode(),
                request.type(), null, null, now, actorId);
        Map<String, Object> before = Map.of();
        questionRepository.save(question); // dòng cha phải tồn tại trước khi ghi phiên bản (FK)
        applySave(question, request, now, actorId);
        questionRepository.save(question);
        audit("QUESTION.CREATE", question, before, auditState(question));
        entityManager.flush();
        return mapper.toDetail(question, mapper.batch());
    }

    /** Sửa câu độc lập (spec 4.5). Trả kèm cờ {@code versionCreated} của hợp đồng. */
    @Transactional
    public UpdateQuestionResponse update(UUID id, UpdateQuestionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, request.expectedRevision());
        Map<String, Object> before = auditState(question);
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        if (request.type() != null && request.type() != question.getType()) {
            throw new BusinessRuleException(
                    ERR_TYPE_IMMUTABLE, null,
                    List.of(new FieldError("type", ERR_TYPE_IMMUTABLE, null)));
        }
        boolean versionCreated = applySave(question, toSaveRequest(request), Instant.now(), actorId);
        audit("QUESTION.UPDATE", question, before, auditState(question));
        entityManager.flush();
        return new UpdateQuestionResponse(mapper.toDetail(question, mapper.batch()), versionCreated);
    }

    /** Catalog references already validated from one batch snapshot for an import transaction. */
    record ImportClassification(UUID subjectItemId, UUID gradeItemId, UUID levelItemId,
            UUID taskTypeItemId, List<vn.unicom.thi360.questionbank.api.KnowledgeRef> knowledge,
            List<UUID> tagItemIds) {}

    /**
     * Save one imported question through the normal version/projection path. The caller locks
     * existing targets in id order, validates every catalog reference against its batch snapshot,
     * and records the single batch audit event in the surrounding transaction.
     */
    void saveImported(Question existing, SaveQuestionRequest request, ImportClassification resolved) {
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        Instant now = Instant.now();
        Question question = existing;
        if (question == null) {
            question = Question.create(UUID.randomUUID(), supportDao.nextQuestionCode(),
                    request.type(), null, null, now, actorId);
            questionRepository.save(question);
        } else if (question.getDeletedAt() != null || question.getParentId() != null
                || question.getType() != request.type()) {
            throw new BusinessRuleException("IMPORT_SOURCE_TYPE_MISMATCH");
        }
        ResolvedClassification classification = new ResolvedClassification(resolved.subjectItemId(),
                resolved.gradeItemId(), resolved.levelItemId(), resolved.taskTypeItemId(), null);
        List<KnowledgeResolved> knowledge = resolved.knowledge().stream()
                .map(ref -> new KnowledgeResolved(ref.nodeId(), ref.primary())).toList();
        applySaveResolved(question, request, now, actorId, classification, knowledge,
                resolved.tagItemIds(), false);
        questionRepository.save(question);
    }


    private static SaveQuestionRequest toSaveRequest(UpdateQuestionRequest request) {
        return new SaveQuestionRequest(
                request.type(), request.subjectCode(), request.gradeCode(), request.difficultyCode(),
                request.taskTypeCode(), request.competencyCode(), request.points(),
                request.knowledge(), request.tagCodes(), request.content(), request.solution(),
                request.commonMistakes(), request.attachmentIds(), request.body(), request.sourceCode());
    }

    /**
     * Phần chung của tạo và sửa: tra mã danh mục, lọc HTML, kiểm mức lưu, đồng bộ câu con
     * (bộ câu hỏi), ghi phiên bản và bảng phân loại. Trả {@code true} khi phiên bản làm việc
     * được tạo mới (câu đã READY/RETIRED — hợp đồng {@code versionCreated}).
     */
    private boolean applySave(Question question, SaveQuestionRequest request, Instant now, UUID actorId) {
        QuestionType type = question.getType();
        Objects.requireNonNull(type, "type must not be null");

        ResolvedClassification classification = resolveClassification(request);
        List<KnowledgeResolved> knowledge = resolveKnowledge(request == null ? null : request.knowledge());
        List<UUID> tagItemIds = resolveTagIds(request == null ? null : request.tagCodes());
        return applySaveResolved(question, request, now, actorId, classification, knowledge, tagItemIds, true);
    }

    private boolean applySaveResolved(Question question, SaveQuestionRequest request, Instant now, UUID actorId,
            ResolvedClassification classification, List<KnowledgeResolved> knowledge,
            List<UUID> tagItemIds, boolean validateChildCatalog) {
        QuestionType type = question.getType();
        Sanitized sanitized = sanitize(request);
        if (validateChildCatalog) {
            validateChildOverrides(sanitized.body());
        }
        QuestionInput input = new QuestionInput(
                type, sanitized.body(), effectivePoints(type, request == null ? null : request.points(), sanitized.body()),
                sanitized.content(), sanitized.solution(), sanitized.commonMistakes(), sanitized.attachmentIds());
        List<QuestionFieldError> errors = QuestionBodyValidator.validateSave(input, assetResolver);
        if (!errors.isEmpty()) {
            throw bodyInvalid(errors);
        }

        QuestionVersion previousWorking = workingVersion(question);
        boolean versionCreated = false;
        Integer targetVersion;
        if (question.getWorkingVersionNo() != null) {
            targetVersion = question.getWorkingVersionNo();
        } else {
            Integer max = versionRepository.findMaxVersionNo(question.getId());
            targetVersion = (max == null ? 0 : max) + 1;
            versionCreated = true;
        }

        QuestionBody snapshotBody = sanitized.body();
        // Khi đã có bản phục vụ, chỉ cấp id trong snapshot; không sửa projection đang phục vụ.
        if (type == QuestionType.QUESTION_SET
                && sanitized.body() instanceof QuestionBody.QuestionSetBody setBody) {
            List<UUID> children = question.getCurrentVersionNo() == null
                    ? syncChildren(question, setBody, classification, knowledge, targetVersion, now, actorId)
                    : prepareChildIds(question, setBody);
            List<QuestionBody.ChildQuestion> withIds = new ArrayList<>(children.size());
            for (int i = 0; i < children.size(); i++) {
                QuestionBody.ChildQuestion child = setBody.children().get(i);
                withIds.add(new QuestionBody.ChildQuestion(child.type(), child.content(), child.body(),
                        child.solution(), child.points(), child.gradeItemId(), child.levelItemId(),
                        child.knowledgeItemIds(), children.get(i)));
            }
            snapshotBody = new QuestionBody.QuestionSetBody(setBody.type(), setBody.passage(), withIds);
        }

        QuestionSnapshot snapshot = new QuestionSnapshot(
                classification.subjectItemId(), classification.gradeItemId(),
                classification.levelItemId(), classification.taskTypeItemId(),
                classification.competencyItemId(), input.points(),
                toSnapshotKnowledge(knowledge), tagItemIds,
                sanitized.content(), sanitized.solution(), sanitized.commonMistakes(),
                sanitized.attachmentIds(), snapshotBody,
                request.sourceCode() != null ? request.sourceCode()
                        : previousWorking != null && previousWorking.getSnapshot().sourceCode() != null
                                ? previousWorking.getSnapshot().sourceCode() : question.getSourceCode());
        entityManager.flush();
        supportDao.reserveSourceCodes(question.getId(),
                question.getCurrentVersionNo() == null ? null : question.getSourceCode(), snapshot.sourceCode());
        upsertVersion(question.getId(), targetVersion, snapshot, actorId, now);
        if (question.getCurrentVersionNo() != null && snapshot.body() instanceof QuestionBody.QuestionSetBody) {
            saveWorkingChildren(question, snapshot, targetVersion, now, actorId);
        }

        if (question.getCurrentVersionNo() == null) {
            projectRow(question, snapshot, targetVersion, now, actorId);
        } else {
            question.setWorkingVersionNo(targetVersion);
            question.touch(now, actorId);
        }
        return versionCreated;
    }

    // ---------------------------------------------------------------- trạng thái

    /** Đưa câu lên Sẵn sàng: kiểm đủ điều kiện (spec 4.5) rồi đóng bản làm việc. */
    @Transactional
    public QuestionSummary markReady(UUID id, Long expectedRevision) {
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, expectedRevision);
        if (question.getStatus() == QuestionStatus.READY && question.getWorkingVersionNo() == null) {
            throw new ConflictException(ERR_ALREADY_READY);
        }
        QuestionVersion working = workingVersion(question);
        if (working == null && question.getCurrentVersionNo() != null) {
            working = versionRepository.findByQuestionIdAndVersionNo(
                    id, question.getCurrentVersionNo()).orElse(null);
        }
        if (working == null || working.getSnapshot() == null) {
            throw new ConflictException(ERR_NOT_READY);
        }
        readinessOrThrow(question, working.getSnapshot());
        Map<String, Object> before = auditState(question);
        UUID oldSubject = question.getStatus() == QuestionStatus.READY ? servingSubject(question) : null;

        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        if (question.getWorkingVersionNo() != null) {
            projectSnapshot(question, working.getSnapshot(), working.getVersionNo(), now, actorId);
        }
        working.setReadyAt(now);
        versionRepository.save(working);
        for (Question child : questionRepository.findByParentIdAndDeletedAtIsNullOrderByPosition(id)) {
            QuestionVersion childWorking = versionRepository
                    .findByQuestionIdAndVersionNo(child.getId(), working.getVersionNo())
                    .orElse(null);
            if (childWorking != null) {
                childWorking.setReadyAt(now);
                versionRepository.save(childWorking);
            }
            child.setCurrentVersionNo(working.getVersionNo());
            child.setStatus(QuestionStatus.READY);
            child.touch(now, actorId);
        }
        question.setCurrentVersionNo(working.getVersionNo());
        question.setWorkingVersionNo(null);
        question.setStatus(QuestionStatus.READY);
        question.touch(now, actorId);
        questionRepository.save(question);
        supportDao.reserveSourceCodes(id, question.getSourceCode(), null);
        supportDao.bumpSourceRevision(question.getSubjectItemId());
        if (oldSubject != null && !Objects.equals(oldSubject, question.getSubjectItemId())) {
            supportDao.bumpSourceRevision(oldSubject);
        }
        audit("QUESTION.READY", question, before, auditState(question));
        entityManager.flush();
        return mapper.toSummary(question, working, mapper.batch());
    }

    /** Ngừng câu và giữ bản làm việc nếu có (spec 4.4). */
    @Transactional
    public QuestionSummary retire(UUID id, Long expectedRevision) {
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, expectedRevision);
        if (question.getStatus() == QuestionStatus.RETIRED) {
            throw new ConflictException(ERR_ALREADY_RETIRED);
        }
        Map<String, Object> before = auditState(question);
        boolean wasReady = question.getStatus() == QuestionStatus.READY;
        UUID oldSubject = wasReady ? servingSubject(question) : null;
        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        for (Question child : questionRepository.findByParentIdAndDeletedAtIsNullOrderByPosition(id)) {
            child.setStatus(QuestionStatus.RETIRED);
            child.touch(now, actorId);
        }
        question.setStatus(QuestionStatus.RETIRED);
        question.touch(now, actorId);
        questionRepository.save(question);
        if (wasReady) {
            supportDao.bumpSourceRevision(oldSubject);
        }
        audit("QUESTION.RETIRE", question, before, auditState(question));
        entityManager.flush();
        return mapper.toSummary(question, mapper.displayedVersion(question), mapper.batch());
    }

    /** Đổi trạng thái hàng loạt; câu lỗi được bỏ qua kèm lý do (spec 4.5). */
    @Transactional
    public BulkQuestionStatusResult bulkReady(String idempotencyKey, BulkQuestionStatusRequest request) {
        return idempotentCommand.execute(idempotencyKey, "question.bulk.ready", request,
                BulkQuestionStatusResult.class, () -> bulkTransition(request, QuestionStatus.READY));
    }

    @Transactional
    public BulkQuestionStatusResult bulkRetire(String idempotencyKey, BulkQuestionStatusRequest request) {
        return idempotentCommand.execute(idempotencyKey, "question.bulk.retire", request,
                BulkQuestionStatusResult.class, () -> bulkTransition(request, QuestionStatus.RETIRED));
    }

    private BulkQuestionStatusResult bulkTransition(
            BulkQuestionStatusRequest request, QuestionStatus target) {
        List<UUID> ids = request == null || request.ids() == null
                ? List.of()
                : request.ids().stream().distinct().toList();
        Map<UUID, Question> found = new HashMap<>();
        for (Question q : questionRepository.findAllByIdInForUpdate(ids)) {
            if (q.getDeletedAt() == null && q.getParentId() == null) {
                found.put(q.getId(), q);
            }
        }
        List<QuestionSummary> updated = new ArrayList<>();
        List<BulkQuestionStatusResult.Skipped> skipped = new ArrayList<>();
        for (UUID id : ids) {
            Question question = found.get(id);
            if (question == null) {
                skipped.add(new BulkQuestionStatusResult.Skipped(id, "NOT_FOUND", null));
                continue;
            }
            if (question.getStatus() == target
                    && !(target == QuestionStatus.READY && question.getWorkingVersionNo() != null)) {
                skipped.add(new BulkQuestionStatusResult.Skipped(id, "ALREADY_IN_STATUS", null));
                continue;
            }
            Map<String, Object> before = auditState(question);
            boolean wasReady = question.getStatus() == QuestionStatus.READY;
            UUID oldSubject = wasReady ? servingSubject(question) : null;
            if (target == QuestionStatus.READY) {
                QuestionVersion working = workingVersion(question);
                if (working == null && question.getCurrentVersionNo() != null) {
                    working = versionRepository.findByQuestionIdAndVersionNo(
                            id, question.getCurrentVersionNo()).orElse(null);
                }
                QuestionFieldError firstError = working == null
                        ? new QuestionFieldError(ERR_NOT_READY, "status", ERR_NOT_READY)
                        : readinessErrors(question, working.getSnapshot());
                if (firstError != null) {
                    skipped.add(new BulkQuestionStatusResult.Skipped(
                            id, "NOT_READY", firstError.code()));
                    continue;
                }
                if (question.getWorkingVersionNo() != null
                        && hasUsedRemovedChild(question, working.getSnapshot())) {
                    skipped.add(new BulkQuestionStatusResult.Skipped(id, "NOT_READY", ERR_IN_USE));
                    continue;
                }
                Instant now = Instant.now();
                UUID actorId = currentUserProvider.requireCurrentUser().id();
                if (question.getWorkingVersionNo() != null) {
                    projectSnapshot(question, working.getSnapshot(), working.getVersionNo(), now, actorId);
                }
                working.setReadyAt(now);
                versionRepository.save(working);
                applyReadyToChildren(question, working.getVersionNo(), now, actorId);
                question.setCurrentVersionNo(working.getVersionNo());
                question.setWorkingVersionNo(null);
                question.setStatus(QuestionStatus.READY);
                supportDao.reserveSourceCodes(id, question.getSourceCode(), null);
                question.touch(now, actorId);
            } else {
                Instant now = Instant.now();
                UUID actorId = currentUserProvider.requireCurrentUser().id();
                applyRetireToChildren(question, now, actorId);
                question.setStatus(QuestionStatus.RETIRED);
                question.touch(now, actorId);
            }
            questionRepository.save(question);
            if (target == QuestionStatus.READY) {
                supportDao.bumpSourceRevision(question.getSubjectItemId());
            }
            if (wasReady && (target != QuestionStatus.READY
                    || !Objects.equals(oldSubject, question.getSubjectItemId()))) {
                supportDao.bumpSourceRevision(oldSubject);
            }
            audit(target == QuestionStatus.READY ? "QUESTION.READY" : "QUESTION.RETIRE",
                    question, before, auditState(question));
            entityManager.flush();
            updated.add(mapper.toSummary(question, mapper.displayedVersion(question), mapper.batch()));
        }
        return new BulkQuestionStatusResult(updated, skipped);
    }

    private boolean hasUsedRemovedChild(Question parent, QuestionSnapshot snapshot) {
        if (!(snapshot.body() instanceof QuestionBody.QuestionSetBody set)) {
            return false;
        }
        Set<UUID> kept = new HashSet<>();
        for (QuestionBody.ChildQuestion child : set.children()) {
            kept.add(child.id());
        }
        for (Question child : questionRepository.findByParentIdAndDeletedAtIsNullOrderByPosition(parent.getId())) {
            if (!kept.contains(child.getId()) && ledgerRepository.existsByQuestionId(child.getId())) {
                return true;
            }
        }
        return false;
    }

    private void applyReadyToChildren(Question parent, int versionNo, Instant now, UUID actorId) {
        for (Question child : questionRepository.findByParentIdAndDeletedAtIsNullOrderByPosition(parent.getId())) {
            versionRepository.findByQuestionIdAndVersionNo(child.getId(), versionNo)
                    .ifPresent(v -> {
                        v.setReadyAt(now);
                        versionRepository.save(v);
                    });
            child.setCurrentVersionNo(versionNo);
            child.setStatus(QuestionStatus.READY);
            child.touch(now, actorId);
        }
    }

    private void applyRetireToChildren(Question parent, Instant now, UUID actorId) {
        for (Question child : questionRepository.findByParentIdAndDeletedAtIsNullOrderByPosition(parent.getId())) {
            child.setStatus(QuestionStatus.RETIRED);
            child.touch(now, actorId);
        }
    }

    // ---------------------------------------------------------------- nhân đôi / xóa

    /** Nhân đôi câu độc lập (kể cả bộ: câu con được nhân đôi với id mới, spec 4.5). */
    @Transactional
    public QuestionDetail duplicate(UUID id, String idempotencyKey) {
        return idempotentCommand.execute(
                idempotencyKey, "question.duplicate", id, null, QuestionDetail.class,
                () -> doDuplicate(id));
    }

    private QuestionDetail doDuplicate(UUID id) {
        Question source = loadStandaloneForUpdate(id);
        QuestionVersion displayed = mapper.displayedVersion(source);
        if (displayed == null) {
            throw new NotFoundException("QUESTION_VERSION_NOT_FOUND", null);
        }
        QuestionSnapshot snapshot = displayed.getSnapshot();
        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();

        Question copy = Question.create(
                UUID.randomUUID(), supportDao.nextQuestionCode(), source.getType(),
                null, null, now, actorId);
        QuestionSnapshot copySnapshot = new QuestionSnapshot(
                snapshot.subjectItemId(), snapshot.gradeItemId(), snapshot.levelItemId(),
                snapshot.taskTypeItemId(), snapshot.competencyItemId(), snapshot.points(),
                snapshot.knowledge(), snapshot.tagItemIds(), snapshot.content(), snapshot.solution(),
                snapshot.commonMistakes(), snapshot.attachmentIds(), snapshot.body(), null);
        if (snapshot.body() instanceof QuestionBody.QuestionSetBody setBody) {
            List<QuestionBody.ChildQuestion> copyChildren = new ArrayList<>(setBody.children().size());
            for (QuestionBody.ChildQuestion child : setBody.children()) {
                copyChildren.add(new QuestionBody.ChildQuestion(
                        child.type(), child.content(), child.body(), child.solution(),
                        child.points(), child.gradeItemId(), child.levelItemId(),
                        child.knowledgeItemIds(), UUID.randomUUID()));
            }
            copySnapshot = new QuestionSnapshot(
                    snapshot.subjectItemId(), snapshot.gradeItemId(), snapshot.levelItemId(),
                    snapshot.taskTypeItemId(), snapshot.competencyItemId(), snapshot.points(),
                    snapshot.knowledge(), snapshot.tagItemIds(), snapshot.content(),
                    snapshot.solution(), snapshot.commonMistakes(), snapshot.attachmentIds(),
                    new QuestionBody.QuestionSetBody(
                            QuestionType.QUESTION_SET, setBody.passage(), copyChildren), null);
        }

        questionRepository.save(copy);
        applyRowFields(copy, copySnapshot, null, 1, now, actorId);
        versionRepository.save(new QuestionVersion(copy.getId(), 1, copySnapshot, actorId, now));
        writeAssetRefs(copy.getId(), 1, copySnapshot);
        saveKnowledgeRows(copy.getId(), fromSnapshotKnowledge(copySnapshot.knowledge()));
        for (UUID tagItemId : copySnapshot.tagItemIds()) {
            tagRepository.save(new QuestionTag(copy.getId(), tagItemId));
        }
        if (copySnapshot.body() instanceof QuestionBody.QuestionSetBody setBody) {
            for (int i = 0; i < setBody.children().size(); i++) {
                QuestionBody.ChildQuestion child = setBody.children().get(i);
                Question row = Question.create(
                        child.id(), copy.getCode() + "." + (i + 1), child.type(),
                        copy.getId(), i, now, actorId);
                QuestionSnapshot childSnapshot = childSnapshot(
                        child, copySnapshot, copy.getPoints());
                applyChildRow(row, child, childSnapshot, 1, copySnapshot.subjectItemId(), now, actorId);
                persistChild(row, childSnapshot, 1, now, actorId);
            }
        }
        audit("QUESTION.DUPLICATE", copy, null, auditState(copy));
        entityManager.flush();
        return mapper.toDetail(copy, mapper.batch());
    }

    /** Xóa mềm câu độc lập; câu đã có trong sổ sử dụng bị chặn 409 (spec 4.5). */
    @Transactional
    public void delete(UUID id, String idempotencyKey, Long expectedRevision) {
        idempotentCommand.execute(idempotencyKey, "question.delete", id, expectedRevision, Void.class,
                () -> {
                    doDelete(id, expectedRevision);
                    return null;
                });
    }

    private void doDelete(UUID id, Long expectedRevision) {
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, expectedRevision);
        List<Question> children = questionRepository
                .findByParentIdAndDeletedAtIsNullOrderByPosition(id);
        List<String> inUseLabels = new ArrayList<>();
        if (ledgerRepository.existsByQuestionId(id)) {
            inUseLabels.add(question.getCode());
        }
        for (Question child : children) {
            if (ledgerRepository.existsByQuestionId(child.getId())) {
                inUseLabels.add(child.getCode());
            }
        }
        if (!inUseLabels.isEmpty()) {
            throw new ConflictException(ERR_IN_USE, null,
                    List.of(new FieldError("id", ERR_IN_USE, String.join(", ", inUseLabels))));
        }
        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        Map<String, Object> before = auditState(question);
        boolean wasReady = question.getStatus() == QuestionStatus.READY;
        question.setDeletedAt(now);
        question.touch(now, actorId);
        for (Question child : children) {
            child.setDeletedAt(now);
            child.touch(now, actorId);
        }
        questionRepository.save(question);
        supportDao.releaseSourceCodes(id);
        if (wasReady) {
            supportDao.bumpSourceRevision(question.getSubjectItemId());
        }
        audit("QUESTION.DELETE", question, before, Map.of("deleted", true));
    }

    // ---------------------------------------------------------------- khôi phục phiên bản

    /**
     * Khôi phục nội dung của một phiên bản cũ vào bản làm việc (spec 4.5): bản chụp đã qua
     * kiểm tra nên ghi lại nguyên trạng; câu con của bộ được đồng bộ (hồi sinh câu con bị
     * xóa mềm nếu bản chụp có, xóa mềm câu con không còn trong bản chụp).
     */
    @Transactional
    public UpdateQuestionResponse restoreVersion(UUID id, int versionNo, Long expectedRevision) {
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, expectedRevision);
        QuestionVersion source = versionRepository
                .findByQuestionIdAndVersionNo(id, versionNo)
                .orElseThrow(() -> new NotFoundException(ERR_VERSION_NOT_FOUND, null));
        QuestionSnapshot snapshot = source.getSnapshot();
        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        Map<String, Object> before = auditState(question);

        Integer targetVersion;
        boolean versionCreated;
        if (question.getWorkingVersionNo() != null) {
            targetVersion = question.getWorkingVersionNo();
            versionCreated = false;
        } else {
            Integer max = versionRepository.findMaxVersionNo(id);
            targetVersion = (max == null ? 0 : max) + 1;
            versionCreated = true;
        }
        supportDao.reserveSourceCodes(id, question.getCurrentVersionNo() == null ? null : question.getSourceCode(),
                snapshot.sourceCode() == null ? question.getSourceCode() : snapshot.sourceCode());
        upsertVersion(id, targetVersion, snapshot, actorId, now);
        if (question.getCurrentVersionNo() != null && snapshot.body() instanceof QuestionBody.QuestionSetBody) {
            saveWorkingChildren(question, snapshot, targetVersion, now, actorId);
        }

        if (question.getCurrentVersionNo() == null) {
            projectSnapshot(question, snapshot, targetVersion, now, actorId);
        } else {
            question.setWorkingVersionNo(targetVersion);
            question.touch(now, actorId);
        }
        questionRepository.save(question);
        audit("QUESTION.RESTORE", question, before, auditState(question));
        entityManager.flush();
        return new UpdateQuestionResponse(mapper.toDetail(question, mapper.batch()), versionCreated);
    }

    // ---------------------------------------------------------------- câu con


    private List<UUID> prepareChildIds(Question parent, QuestionBody.QuestionSetBody body) {
        Set<UUID> seen = new HashSet<>();
        List<UUID> ids = new ArrayList<>(body.children().size());
        for (QuestionBody.ChildQuestion child : body.children()) {
            UUID id = child.id() == null ? UUID.randomUUID() : child.id();
            if (!seen.add(id)) {
                throw new BusinessRuleException(ERR_BODY_INVALID, null,
                        List.of(new FieldError("body.children", ERR_DUPLICATE_ID, null)));
            }
            questionRepository.findById(id).ifPresent(existing -> {
                if (!Objects.equals(existing.getParentId(), parent.getId()) || existing.getType() != child.type()) {
                    throw new BusinessRuleException(ERR_BODY_INVALID, null,
                            List.of(new FieldError("body.children", ERR_TYPE_IMMUTABLE, null)));
                }
            });
            ids.add(id);
        }
        return ids;
    }
    /** Save child-version refs without modifying the serving projection. */
    private void saveWorkingChildren(Question parent, QuestionSnapshot snapshot, int versionNo,
            Instant now, UUID actorId) {
        QuestionBody.QuestionSetBody body = (QuestionBody.QuestionSetBody) snapshot.body();
        List<Question> existing = questionRepository.findAllByParentId(parent.getId());
        Map<UUID, Question> byId = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (Question row : existing) {
            byId.put(row.getId(), row);
            codes.add(row.getCode());
        }
        Set<UUID> kept = new HashSet<>();
        for (int i = 0; i < body.children().size(); i++) {
            QuestionBody.ChildQuestion child = body.children().get(i);
            UUID childId = child.id();
            kept.add(childId);
            if (!byId.containsKey(childId)) {
                Question row = Question.create(childId, nextChildCode(parent, codes), child.type(),
                        parent.getId(), i, now, actorId);
                row.setDeletedAt(now);
                questionRepository.save(row);
            }
            upsertVersion(childId, versionNo, childSnapshot(child, snapshot, snapshot.points()), actorId, now);
        }
        for (Question row : existing) {
            if (!kept.contains(row.getId())) {
                assetRefRepository.deleteByQuestionIdAndVersionNo(row.getId(), versionNo);
            }
        }
    }


    /**
     * Đồng bộ dòng câu con với {@code children[]} của thân: tạo câu con mới (id do máy chủ
     * hoặc client cung cấp), cập nhật câu con có sẵn, xóa mềm câu con bị bỏ ra khỏi bộ (chặn
     * khi câu con đã dùng — 409 {@code QUESTION_IN_USE}).
     */
    private List<UUID> syncChildren(
            Question parent,
            QuestionBody.QuestionSetBody setBody,
            ResolvedClassification parentClassification,
            List<KnowledgeResolved> parentKnowledge,
            int targetVersion,
            Instant now,
            UUID actorId) {
        List<QuestionBody.ChildQuestion> children = setBody.children();
        Set<UUID> ids = new HashSet<>();
        for (QuestionBody.ChildQuestion child : children) {
            if (child.id() != null && !ids.add(child.id())) {
                throw new BusinessRuleException(ERR_BODY_INVALID, null,
                        List.of(new FieldError("body.children", ERR_DUPLICATE_ID, null)));
            }
        }
        Map<UUID, Question> existing = new HashMap<>();
        for (Question row : questionRepository.findAllByParentId(parent.getId())) {
            existing.put(row.getId(), row);
        }
        Set<String> usedCodes = new HashSet<>();
        existing.values().forEach(row -> usedCodes.add(row.getCode()));
        List<UUID> kept = new ArrayList<>(children.size());
        List<UUID> parentKnowledgeIds = parentKnowledge.stream().map(KnowledgeResolved::itemId).toList();

        for (int i = 0; i < children.size(); i++) {
            QuestionBody.ChildQuestion child = children.get(i);
            UUID childId = child.id() != null ? child.id() : UUID.randomUUID();
            kept.add(childId);
            Question row = existing.get(childId);
            if (row == null) {
                if (child.id() != null && questionRepository.findById(childId).isPresent()) {
                    // Id do client cấp đã thuộc câu khác (kể cả đã xóa mềm) → 422 như prepareChildIds.
                    throw new BusinessRuleException(ERR_BODY_INVALID, null,
                            List.of(new FieldError("body.children", ERR_TYPE_IMMUTABLE, null)));
                }
                row = Question.create(
                        childId, nextChildCode(parent, usedCodes), child.type(),
                        parent.getId(), i, now, actorId);
            } else {
                if (row.getType() != child.type()) {
                    throw new BusinessRuleException(ERR_TYPE_IMMUTABLE, null,
                            List.of(new FieldError(
                                    "body.children[" + i + "].type", ERR_TYPE_IMMUTABLE, null)));
                }
                row.setDeletedAt(null);
                row.setPosition(i);
            }
            UUID effectiveGrade = child.gradeItemId() != null
                    ? child.gradeItemId()
                    : parentClassification.gradeItemId();
            UUID effectiveLevel = child.levelItemId() != null
                    ? child.levelItemId()
                    : parentClassification.levelItemId();
            List<UUID> effectiveKnowledge = child.knowledgeItemIds() == null
                    ? parentKnowledgeIds
                    : child.knowledgeItemIds();
            QuestionSnapshot childSnapshot = new QuestionSnapshot(
                    parentClassification.subjectItemId(), effectiveGrade, effectiveLevel,
                    parentClassification.taskTypeItemId(), parentClassification.competencyItemId(),
                    child.points() != null ? child.points() : BigDecimal.ONE,
                    effectiveKnowledge.stream()
                            .map(k -> new QuestionSnapshot.KnowledgeRef(k, false)).toList(),
                    List.of(), child.content(), child.solution(),
                    List.of(), List.of(), child.body(), null);

            applyChildRow(row, child, childSnapshot, targetVersion,
                    parentClassification.subjectItemId(), now, actorId);
            questionRepository.save(row);
            upsertVersion(childId, targetVersion, childSnapshot, actorId, now);
            knowledgeRepository.deleteByQuestionId(childId);
            saveKnowledgeIds(childId, effectiveKnowledge);
        }
        for (Map.Entry<UUID, Question> entry : existing.entrySet()) {
            Question leftover = entry.getValue();
            if (leftover.getDeletedAt() == null && !kept.contains(leftover.getId())) {
                if (ledgerRepository.existsByQuestionId(leftover.getId())) {
                    throw new ConflictException(ERR_IN_USE, null,
                            List.of(new FieldError("body.children", ERR_IN_USE, leftover.getCode())));
                }
                leftover.setDeletedAt(now);
                leftover.touch(now, actorId);
            }
        }
        return kept;
    }

    private void restoreChildren(
            Question parent,
            QuestionBody.QuestionSetBody setBody,
            QuestionSnapshot parentSnapshot,
            int targetVersion,
            Instant now,
            UUID actorId) {
        Map<UUID, Question> existing = new HashMap<>();
        for (Question row : questionRepository.findAllByParentId(parent.getId())) {
            existing.put(row.getId(), row);
        }
        Set<String> usedCodes = new HashSet<>();
        existing.values().forEach(row -> usedCodes.add(row.getCode()));
        List<UUID> parentKnowledgeIds = parentSnapshot.knowledge().stream()
                .map(QuestionSnapshot.KnowledgeRef::itemId).toList();
        List<UUID> kept = new ArrayList<>(setBody.children().size());
        for (int i = 0; i < setBody.children().size(); i++) {
            QuestionBody.ChildQuestion child = setBody.children().get(i);
            UUID childId = child.id() != null ? child.id() : UUID.randomUUID();
            kept.add(childId);
            Question row = existing.get(childId);
            boolean isNew = row == null;
            if (isNew) {
                row = Question.create(
                        childId, nextChildCode(parent, usedCodes), child.type(),
                        parent.getId(), i, now, actorId);
            } else {
                row.setDeletedAt(null);
                row.setPosition(i);
                row.setType(child.type());
            }
            UUID effectiveGrade = child.gradeItemId() == null ? parentSnapshot.gradeItemId() : child.gradeItemId();
            UUID effectiveLevel = child.levelItemId() == null ? parentSnapshot.levelItemId() : child.levelItemId();
            List<UUID> effectiveKnowledge = child.knowledgeItemIds() == null
                    ? parentKnowledgeIds : child.knowledgeItemIds();

            QuestionSnapshot childSnapshot = new QuestionSnapshot(
                    parentSnapshot.subjectItemId(),
                    effectiveGrade, effectiveLevel,
                    parentSnapshot.taskTypeItemId(), parentSnapshot.competencyItemId(),
                    child.points() != null ? child.points() : BigDecimal.ONE,
                    effectiveKnowledge.stream()
                            .map(k -> new QuestionSnapshot.KnowledgeRef(k, false)).toList(),
                    List.of(), child.content(), child.solution(),
                    List.of(), List.of(), child.body(), null);
            applyChildRow(row, child, childSnapshot, targetVersion,
                    parentSnapshot.subjectItemId(), now, actorId);
            questionRepository.save(row);
            upsertVersion(childId, targetVersion, childSnapshot, actorId, now);
            knowledgeRepository.deleteByQuestionId(childId);
            saveKnowledgeIds(childId, effectiveKnowledge);
        }
        for (Map.Entry<UUID, Question> entry : existing.entrySet()) {
            Question leftover = entry.getValue();
            if (leftover.getDeletedAt() == null && !kept.contains(leftover.getId())) {
                if (ledgerRepository.existsByQuestionId(leftover.getId())) {
                    throw new ConflictException(ERR_IN_USE, null,
                            List.of(new FieldError("body.children", ERR_IN_USE, leftover.getCode())));
                }
                leftover.setDeletedAt(now);
                leftover.touch(now, actorId);
            }
        }
    }

    private String nextChildCode(Question parent, Set<String> usedCodes) {
        int suffix = 1;
        String code;
        do {
            code = parent.getCode() + "." + suffix++;
        } while (!usedCodes.add(code));
        return code;
    }

    private void applyChildRow(
            Question row,
            QuestionBody.ChildQuestion child,
            QuestionSnapshot snapshot,
            int versionNo,
            UUID parentSubjectItemId,
            Instant now,
            UUID actorId) {
        row.setSubjectItemId(parentSubjectItemId);
        row.setGradeItemId(child.gradeItemId());
        row.setLevelItemId(child.levelItemId());
        row.setPoints(child.points() != null ? child.points() : BigDecimal.ONE);
        row.setContentText(QuestionSearchNormalizer.contentText(child.content(), child.body()));
        row.setSearchText(QuestionSearchNormalizer.searchText(row.getCode(), child.content(), child.body()));
        row.setHasSolution(child.solution() != null && !child.solution().isBlank());
        row.setStatus(QuestionStatus.DRAFT);
        row.setWorkingVersionNo(null);
        row.setCurrentVersionNo(row.getCurrentVersionNo());
        row.touch(now, actorId);
    }

    private void persistChild(
            Question row, QuestionSnapshot snapshot, int versionNo, Instant now, UUID actorId) {
        questionRepository.save(row);
        versionRepository.save(new QuestionVersion(row.getId(), versionNo, snapshot, actorId, now));
        writeAssetRefs(row.getId(), versionNo, snapshot);
        saveKnowledgeIds(row.getId(),
                snapshot.knowledge().stream().map(QuestionSnapshot.KnowledgeRef::itemId).toList());
    }

    private static QuestionSnapshot childSnapshot(
            QuestionBody.ChildQuestion child, QuestionSnapshot parent, BigDecimal parentPoints) {
        UUID grade = child.gradeItemId() != null ? child.gradeItemId() : parent.gradeItemId();
        UUID level = child.levelItemId() != null ? child.levelItemId() : parent.levelItemId();
        List<UUID> knowledgeIds = child.knowledgeItemIds() == null
                ? parent.knowledge().stream().map(QuestionSnapshot.KnowledgeRef::itemId).toList()
                : child.knowledgeItemIds();
        return new QuestionSnapshot(
                parent.subjectItemId(), grade, level,
                parent.taskTypeItemId(), parent.competencyItemId(),
                child.points() != null ? child.points() : BigDecimal.ONE,
                knowledgeIds.stream().map(k -> new QuestionSnapshot.KnowledgeRef(k, false)).toList(),
                List.of(), child.content(), child.solution(), List.of(), List.of(), child.body(), null);
    }

    // ---------------------------------------------------------------- lọc & kiểm tra

    private record Sanitized(
            String content, String solution, List<String> commonMistakes,
            List<UUID> attachmentIds, QuestionBody body) {}

    private Sanitized sanitize(SaveQuestionRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        String content = cleanField(request == null ? null : request.content(), "content", fieldErrors);
        String solution = cleanField(request == null ? null : request.solution(), "solution", fieldErrors);
        List<String> mistakes = new ArrayList<>();
        List<String> rawMistakes = request == null ? null : request.commonMistakes();
        for (int i = 0; rawMistakes != null && i < rawMistakes.size(); i++) {
            mistakes.add(cleanField(rawMistakes.get(i), "commonMistakes[" + i + "]", fieldErrors));
        }
        QuestionBody body = sanitizeBody(request == null ? null : request.body(), "body", fieldErrors);
        if (!fieldErrors.isEmpty()) {
            throw new BusinessRuleException(ERR_UNSAFE, null, fieldErrors);
        }
        return new Sanitized(content, solution, List.copyOf(mistakes),
                request == null || request.attachmentIds() == null
                        ? List.of() : List.copyOf(request.attachmentIds()),
                body);
    }

    private String cleanField(String html, String path, List<FieldError> errors) {
        if (html == null) {
            return null;
        }
        HtmlContentSanitizer.Result result =
                sanitizer.sanitize(html, HtmlContentSanitizer.Options.withImages());
        for (HtmlContentSanitizer.Violation v : result.violations()) {
            errors.add(new FieldError(path, ERR_UNSAFE, translated(ERR_UNSAFE) + " — " + describe(v)));
        }
        return result.html();
    }

    private QuestionBody sanitizeBody(QuestionBody body, String path, List<FieldError> errors) {
        if (body == null) {
            return null;
        }
        return switch (body) {
            case QuestionBody.SingleChoiceBody b -> new QuestionBody.SingleChoiceBody(
                    b.type(), sanitizeOptions(b.options(), path + ".options", errors));
            case QuestionBody.MultipleChoiceBody b -> new QuestionBody.MultipleChoiceBody(
                    b.type(), sanitizeOptions(b.options(), path + ".options", errors), b.scoringRule());
            case QuestionBody.ShortAnswerBody b -> new QuestionBody.ShortAnswerBody(
                    b.type(), b.answers(), cleanField(b.inputHint(), path + ".inputHint", errors));
            case QuestionBody.ClozeBody b -> new QuestionBody.ClozeBody(
                    b.type(),
                    cleanField(b.passage(), path + ".passage", errors),
                    b.blanks().stream()
                            .map(bl -> new QuestionBody.ClozeBlank(
                                    bl.id(),
                                    bl.answers().stream()
                                            .map(a -> cleanField(a, path + ".blanks", errors))
                                            .toList(),
                                    bl.points()))
                            .toList());
            case QuestionBody.QuestionSetBody b -> new QuestionBody.QuestionSetBody(
                    b.type(),
                    cleanField(b.passage(), path + ".passage", errors),
                    sanitizeChildren(b.children(), path + ".children", errors));
            case QuestionBody.TrueFalseBody b -> new QuestionBody.TrueFalseBody(
                    b.type(),
                    b.statements().stream()
                            .map(s -> new QuestionBody.TrueFalseStatement(
                                    s.id(),
                                    cleanField(s.content(), path + ".statements", errors),
                                    s.answer(), s.points()))
                            .toList(),
                    b.scoringMethod(), b.scoreTable());
            case QuestionBody.MatchingBody b -> new QuestionBody.MatchingBody(
                    b.type(),
                    sanitizeSides(b.left(), path + ".left", errors),
                    sanitizeSides(b.right(), path + ".right", errors),
                    b.pairs(), b.reuseRight());
            case QuestionBody.EssayBody b -> new QuestionBody.EssayBody(
                    b.type(),
                    cleanField(b.gradingGuide(), path + ".gradingGuide", errors),
                    b.rubric().stream()
                            .map(c -> new QuestionBody.EssayCriterion(
                                    c.id(),
                                    cleanField(c.description(), path + ".rubric", errors),
                                    c.points()))
                            .toList(),
                    cleanField(b.sampleAnswer(), path + ".sampleAnswer", errors), b.wordLimit());
        };
    }

    private List<QuestionBody.ChoiceOption> sanitizeOptions(
            List<QuestionBody.ChoiceOption> options, String path, List<FieldError> errors) {
        if (options == null) {
            return List.of();
        }
        List<QuestionBody.ChoiceOption> cleaned = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            QuestionBody.ChoiceOption o = options.get(i);
            cleaned.add(new QuestionBody.ChoiceOption(
                    o.id(), cleanField(o.content(), path + "[" + i + "].content", errors), o.correct()));
        }
        return cleaned;
    }

    private List<QuestionBody.MatchingSide> sanitizeSides(
            List<QuestionBody.MatchingSide> sides, String path, List<FieldError> errors) {
        if (sides == null) {
            return List.of();
        }
        List<QuestionBody.MatchingSide> cleaned = new ArrayList<>(sides.size());
        for (int i = 0; i < sides.size(); i++) {
            QuestionBody.MatchingSide s = sides.get(i);
            cleaned.add(new QuestionBody.MatchingSide(
                    s.id(), cleanField(s.content(), path + "[" + i + "].content", errors)));
        }
        return cleaned;
    }

    private List<QuestionBody.ChildQuestion> sanitizeChildren(
            List<QuestionBody.ChildQuestion> children, String path, List<FieldError> errors) {
        if (children == null) {
            return List.of();
        }
        List<QuestionBody.ChildQuestion> cleaned = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            QuestionBody.ChildQuestion c = children.get(i);
            cleaned.add(new QuestionBody.ChildQuestion(
                    c.type(),
                    cleanField(c.content(), path + "[" + i + "].content", errors),
                    sanitizeBody(c.body(), path + "[" + i + "].body", errors),
                    cleanField(c.solution(), path + "[" + i + "].solution", errors),
                    c.points(), c.gradeItemId(), c.levelItemId(), c.knowledgeItemIds(), c.id()));
        }
        return cleaned;
    }

    private static String describe(HtmlContentSanitizer.Violation v) {
        if (v.attribute() == null) {
            return "removed tag <" + v.tag() + ">";
        }
        return "removed attribute " + v.attribute() + " of <" + v.tag() + ">";
    }

    // ---------------------------------------------------------------- tra danh mục

    private record ResolvedClassification(
            UUID subjectItemId, UUID gradeItemId, UUID levelItemId,
            UUID taskTypeItemId, UUID competencyItemId) {}

    private ResolvedClassification resolveClassification(SaveQuestionRequest request) {
        return new ResolvedClassification(
                resolveItemId("SUBJECT", request == null ? null : request.subjectCode(), "subjectCode"),
                resolveItemId("GRADE", request == null ? null : request.gradeCode(), "gradeCode"),
                resolveItemId("QUESTION_LEVEL", request == null ? null : request.difficultyCode(), "difficultyCode"),
                resolveItemId("TASK_TYPE", request == null ? null : request.taskTypeCode(), "taskTypeCode"),
                resolveItemId("COMPETENCY", request == null ? null : request.competencyCode(), "competencyCode"));
    }

    private void validateChildOverrides(QuestionBody body) {
        List<QuestionFieldError> errors = childOverrideErrors(body, catalogLookup);
        if (!errors.isEmpty()) {
            throw bodyInvalid(errors);
        }
    }

    static List<QuestionFieldError> childOverrideErrors(QuestionBody body, CatalogLookup catalogLookup) {
        List<QuestionFieldError> errors = new ArrayList<>();
        if (!(body instanceof QuestionBody.QuestionSetBody set)) {
            return errors;
        }
        for (int i = 0; i < set.children().size(); i++) {
            QuestionBody.ChildQuestion child = set.children().get(i);
            String path = "body.children[" + i + "]";
            checkChildCatalogItem(child.gradeItemId(), "GRADE", path + ".gradeItemId", catalogLookup, errors);
            checkChildCatalogItem(child.levelItemId(), "QUESTION_LEVEL", path + ".levelItemId", catalogLookup, errors);
            Set<UUID> seen = new HashSet<>();
            if (child.knowledgeItemIds() != null) {
                for (UUID id : child.knowledgeItemIds()) {
                    if (!seen.add(id)) {
                        errors.add(new QuestionFieldError(ERR_DUPLICATE_ID, path + ".knowledgeItemIds", ERR_DUPLICATE_ID));
                    } else {
                        checkChildCatalogItem(id, "KNOWLEDGE", path + ".knowledgeItemIds", catalogLookup, errors);
                    }
                }
            }
        }
        return errors;
    }

    private static void checkChildCatalogItem(UUID id, String group, String field,
            CatalogLookup catalogLookup, List<QuestionFieldError> errors) {
        if (id == null) {
            return;
        }
        boolean valid = catalogLookup.findItem(id)
                .filter(item -> group.equals(item.groupCode()))
                .filter(item -> catalogLookup.findActiveItem(group, item.code())
                        .map(active -> active.id().equals(id)).orElse(false))
                .isPresent();
        if (!valid) {
            errors.add(new QuestionFieldError(ERR_CATALOG_UNKNOWN, field, ERR_CATALOG_UNKNOWN));
        }
    }

    private UUID resolveItemId(String groupCode, String itemCode, String field) {
        if (itemCode == null || itemCode.isBlank()) {
            return null;
        }
        return catalogLookup.findActiveItem(groupCode, itemCode.trim())
                .map(CatalogItemView::id)
                .orElseThrow(() -> new ValidationException(List.of(
                        new FieldError(field, ERR_CATALOG_UNKNOWN, translated(ERR_CATALOG_UNKNOWN) + ": " + itemCode))));
    }

    private record KnowledgeResolved(UUID itemId, boolean primary) {}

    private List<KnowledgeResolved> resolveKnowledge(
            List<vn.unicom.thi360.questionbank.api.KnowledgeRef> knowledge) {
        if (knowledge == null || knowledge.isEmpty()) {
            return List.of();
        }
        if (knowledge.stream().filter(Objects::nonNull).filter(ref -> ref.primary()).count() > 1) {
            throw new BusinessRuleException(ERR_BODY_INVALID, null,
                    List.of(new FieldError("knowledge", ERR_BODY_INVALID, null)));
        }
        List<KnowledgeResolved> resolved = new ArrayList<>(knowledge.size());
        Set<UUID> seen = new HashSet<>();
        for (var ref : knowledge) {
            if (ref == null || ref.nodeId() == null || !seen.add(ref.nodeId())) {
                continue;
            }
            CatalogItemView item = catalogLookup.findItem(ref.nodeId()).orElseThrow(() -> {
                FieldError error = new FieldError("knowledge", ERR_CATALOG_UNKNOWN,
                        translated(ERR_CATALOG_UNKNOWN) + ": " + ref.nodeId());
                return new ValidationException(List.of(error));
            });
            if (!"KNOWLEDGE".equals(item.groupCode())) {
                FieldError error = new FieldError("knowledge", ERR_CATALOG_UNKNOWN,
                        translated(ERR_CATALOG_UNKNOWN) + ": " + item.code());
                throw new ValidationException(List.of(error));
            }
            resolved.add(new KnowledgeResolved(ref.nodeId(), ref.primary()));
        }
        if (resolved.stream().noneMatch(KnowledgeResolved::primary) && !resolved.isEmpty()) {
            resolved.set(0, new KnowledgeResolved(resolved.get(0).itemId(), true));
        }
        return List.copyOf(resolved);
    }

    private List<UUID> resolveTagIds(List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(tagCodes.size());
        Set<UUID> seen = new HashSet<>();
        for (String code : tagCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            UUID id = catalogLookup.findActiveItem("QUESTION_TAG", code.trim())
                    .map(CatalogItemView::id)
                    .orElseThrow(() -> new ValidationException(List.of(
                            new FieldError("tagCodes", ERR_CATALOG_UNKNOWN,
                                    translated(ERR_CATALOG_UNKNOWN) + ": " + code))));
            if (seen.add(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    // ---------------------------------------------------------------- kiểm tra Sẵn sàng

    private void readinessOrThrow(Question question, QuestionSnapshot snapshot) {
        if (snapshot != null) {
            validateChildOverrides(snapshot.body());
        }
        QuestionFieldError first = readinessErrors(question, snapshot);
        if (first != null) {
            // Gom toàn bộ lỗi, không chỉ lỗi đầu.
            List<QuestionFieldError> errors = readinessErrorList(question, snapshot);
            throw new BusinessRuleException(ERR_NOT_READY, null, toFieldErrors(errors));
        }
    }

    private QuestionFieldError readinessErrors(Question question, QuestionSnapshot snapshot) {
        List<QuestionFieldError> errors = readinessErrorList(question, snapshot);
        return errors.isEmpty() ? null : errors.get(0);
    }

    private List<QuestionFieldError> readinessErrorList(Question question, QuestionSnapshot snapshot) {
        if (snapshot == null) {
            return List.of(new QuestionFieldError(ERR_NOT_READY, "snapshot", ERR_NOT_READY));
        }
        List<QuestionFieldError> errors = new ArrayList<>(childOverrideErrors(snapshot.body(), catalogLookup));
        QuestionInput input = new QuestionInput(
                question.getType(), snapshot.body(), snapshot.points(),
                snapshot.content(), snapshot.solution(), snapshot.commonMistakes(),
                snapshot.attachmentIds());
        var classification = new QuestionBodyValidator.ClassificationRequirement(
                snapshot.subjectItemId() != null,
                snapshot.gradeItemId() != null,
                snapshot.levelItemId() != null);
        boolean hasPrimary = snapshot.knowledge().stream().anyMatch(QuestionSnapshot.KnowledgeRef::primary);
        errors.addAll(QuestionBodyValidator.validateReady(input, classification, hasPrimary, assetResolver).errors());
        return errors;
    }

    // ---------------------------------------------------------------- trợ giúp

    private Question loadStandaloneForUpdate(UUID id) {
        Question question = questionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (question.getDeletedAt() != null || question.getParentId() != null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        return question;
    }

    private void checkRevision(Question question, Long expectedRevision) {
        if (expectedRevision == null || !Objects.equals(question.getRevision(), expectedRevision)) {
            throw new VersionConflictException(ERR_REVISION, null);
        }
    }

    private QuestionVersion workingVersion(Question question) {
        Integer workingNo = question.getWorkingVersionNo();
        if (workingNo == null) {
            return null;
        }
        return versionRepository.findByQuestionIdAndVersionNo(question.getId(), workingNo)
                .orElse(null);
    }

    private UUID servingSubject(Question question) {
        if (question.getCurrentVersionNo() == null) {
            return null;
        }
        return versionRepository.findByQuestionIdAndVersionNo(
                        question.getId(), question.getCurrentVersionNo())
                .map(QuestionVersion::getSnapshot)
                .map(QuestionSnapshot::subjectItemId)
                .orElse(null);
    }

    /**
     * Ghi/ghi đè một phiên bản câu (model note 3): lưu tại chỗ thay thế chỉ các ref của
     * chính phiên bản đó; phiên bản mới thêm dòng. Tài nguyên dùng bởi phiên bản đọc từ
     * HTML đã lọc của snapshot + {@code attachmentIds} — cùng transaction, cùng dòng câu
     * dùng tài nguyên (quyết định lane 8.4).
     */
    private void upsertVersion(
            UUID questionId, int versionNo, QuestionSnapshot snapshot, UUID actorId, Instant now) {
        QuestionVersion version = versionRepository
                .findByQuestionIdAndVersionNo(questionId, versionNo)
                .orElse(null);
        if (version == null) {
            versionRepository.save(
                    new QuestionVersion(questionId, versionNo, snapshot, actorId, now));
        } else {
            version.setSnapshot(snapshot);
            version.setReadyAt(null);
            versionRepository.save(version);
        }
        writeAssetRefs(questionId, versionNo, snapshot);
    }

    private void writeAssetRefs(UUID questionId, int versionNo, QuestionSnapshot snapshot) {
        assetRefRepository.deleteByQuestionIdAndVersionNo(questionId, versionNo);
        for (UUID assetId : QuestionAssetRefCollector.assetIdsOf(snapshot)) {
            assetRefRepository.save(new QuestionAssetRef(assetId, questionId, versionNo));
        }
    }

    private void projectSnapshot(Question question, QuestionSnapshot snapshot, int versionNo,
            Instant now, UUID actorId) {
        projectRow(question, snapshot, versionNo, now, actorId);
        if (snapshot.body() instanceof QuestionBody.QuestionSetBody body) {
            restoreChildren(question, body, snapshot, versionNo, now, actorId);
        }
    }

    private void projectRow(Question question, QuestionSnapshot snapshot, int versionNo,
            Instant now, UUID actorId) {
        applyRowFields(question, snapshot, snapshot.sourceCode(), versionNo, now, actorId);
        knowledgeRepository.deleteByQuestionId(question.getId());
        saveKnowledgeRows(question.getId(), fromSnapshotKnowledge(snapshot.knowledge()));
        tagRepository.deleteByQuestionId(question.getId());
        for (UUID tagItemId : snapshot.tagItemIds()) {
            tagRepository.save(new QuestionTag(question.getId(), tagItemId));
        }
    }

    private void applyRowFields(
            Question question, QuestionSnapshot snapshot, String sourceCode,
            int workingVersionNo, Instant now, UUID actorId) {
        question.setSubjectItemId(snapshot.subjectItemId());
        question.setGradeItemId(snapshot.gradeItemId());
        question.setLevelItemId(snapshot.levelItemId());
        question.setTaskTypeItemId(snapshot.taskTypeItemId());
        question.setCompetencyItemId(snapshot.competencyItemId());
        question.setPoints(snapshot.points());
        question.setContentText(
                QuestionSearchNormalizer.contentText(snapshot.content(), snapshot.body()));
        question.setSearchText(QuestionSearchNormalizer.searchText(
                question.getCode(), snapshot.content(), snapshot.body()));
        question.setHasSolution(
                snapshot.solution() != null && !snapshot.solution().isBlank());
        setSourceCode(question, sourceCode);
        question.setWorkingVersionNo(workingVersionNo);
        question.touch(now, actorId);
    }

    private void setSourceCode(Question question, String sourceCode) {
        if (sourceCode == null) {
            return;
        }
        question.setSourceCode(sourceCode.isBlank() ? null : sourceCode.trim());
        question.setSourceCodeNormalized(sourceCode.isBlank()
                ? null
                : sourceCode.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private void saveKnowledgeRows(UUID questionId, List<KnowledgeResolved> knowledge) {
        for (KnowledgeResolved k : knowledge) {
            knowledgeRepository.save(new QuestionKnowledge(questionId, k.itemId(), k.primary()));
        }
    }

    private void saveKnowledgeIds(UUID questionId, List<UUID> knowledgeItemIds) {
        for (UUID itemId : knowledgeItemIds) {
            knowledgeRepository.save(new QuestionKnowledge(questionId, itemId, false));
        }
    }

    private static List<QuestionSnapshot.KnowledgeRef> toSnapshotKnowledge(
            List<KnowledgeResolved> knowledge) {
        return knowledge.stream()
                .map(k -> new QuestionSnapshot.KnowledgeRef(k.itemId(), k.primary()))
                .toList();
    }

    private static List<KnowledgeResolved> fromSnapshotKnowledge(
            List<QuestionSnapshot.KnowledgeRef> knowledge) {
        return knowledge.stream()
                .map(k -> new KnowledgeResolved(k.itemId(), k.primary()))
                .toList();
    }

    private static BigDecimal effectivePoints(
            QuestionType type, BigDecimal requested, QuestionBody body) {
        return switch (type) {
            case CLOZE -> {
                if (body instanceof QuestionBody.ClozeBody cloze) {
                    yield cloze.blanks().stream()
                            .map(b -> b.points() != null ? b.points() : BigDecimal.ONE)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
                yield BigDecimal.ONE;
            }
            case QUESTION_SET -> {
                if (body instanceof QuestionBody.QuestionSetBody set) {
                    yield set.children().stream()
                            .map(c -> c.points() != null ? c.points() : BigDecimal.ONE)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
                yield BigDecimal.ONE;
            }
            default -> requested != null ? requested : BigDecimal.ONE;
        };
    }

    private BusinessRuleException bodyInvalid(List<QuestionFieldError> errors) {
        return new BusinessRuleException(ERR_BODY_INVALID, null, toFieldErrors(errors));
    }

    /** Lỗi trường 422: {@code message} là bản dịch của mã (khóa {@code messages*.properties}). */
    private List<FieldError> toFieldErrors(List<QuestionFieldError> errors) {
        return errors.stream()
                .map(e -> new FieldError(e.path(), e.code(), translated(e.code())))
                .toList();
    }

    private String translated(String code) {
        String text = messages.get(code);
        return text == null || text.isBlank() ? code : text;
    }

    private void audit(
            String action, Question question, Map<String, Object> before, Map<String, Object> after) {
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .target(TARGET, question.getId().toString(), question.getCode())
                .before(before)
                .after(after)
                .build());
    }

    private Map<String, Object> auditState(Question question) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", question.getCode());
        state.put("type", question.getType() == null ? null : question.getType().name());
        state.put("status", question.getStatus() == null ? null : question.getStatus().name());
        state.put("currentVersionNo", question.getCurrentVersionNo());
        state.put("workingVersionNo", question.getWorkingVersionNo());
        QuestionVersion displayed = mapper.displayedVersion(question);
        QuestionSnapshot snapshot = displayed == null ? null : displayed.getSnapshot();
        state.put("subjectItemId", snapshot == null ? question.getSubjectItemId() : snapshot.subjectItemId());
        state.put("gradeItemId", snapshot == null ? question.getGradeItemId() : snapshot.gradeItemId());
        state.put("levelItemId", snapshot == null ? question.getLevelItemId() : snapshot.levelItemId());
        state.put("taskTypeItemId", snapshot == null ? question.getTaskTypeItemId() : snapshot.taskTypeItemId());
        state.put("competencyItemId", snapshot == null ? question.getCompetencyItemId() : snapshot.competencyItemId());
        state.put("points", snapshot == null ? question.getPoints() : snapshot.points());
        state.put("sourceCode", snapshot == null ? question.getSourceCode() : snapshot.sourceCode());
        state.put("knowledge", snapshot == null ? List.of() : snapshot.knowledge().stream()
                .map(k -> Map.of("itemId", k.itemId(), "primary", k.primary())).toList());
        state.put("tagItemIds", snapshot == null ? List.of() : snapshot.tagItemIds());
        state.put("attachmentIds", snapshot == null ? List.of() : snapshot.attachmentIds());
        state.put("snapshotSha256", snapshot == null ? null : snapshotFingerprint(snapshot));
        return state;
    }
    private String snapshotFingerprint(QuestionSnapshot snapshot) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(snapshot)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot fingerprint question snapshot", e);
        }
    }
}
