package vn.unicom.thi360.questionbank.application;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import vn.unicom.thi360.questionbank.domain.QuestionFieldError;
import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
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
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;
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

    private static final int CHILD_MIN = 2;

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionKnowledgeRepository knowledgeRepository;
    private final QuestionTagRepository tagRepository;
    private final QuestionUsageLedgerRepository ledgerRepository;
    private final QuestionSupportDao supportDao;
    private final QuestionHtmlSanitizer sanitizer;
    private final QuestionAssetResolver assetResolver;
    private final CatalogLookup catalogLookup;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotentCommand idempotentCommand;
    private final QuestionUsageProvider usageProvider;
    private final QuestionMapper mapper;
    private final AppMessages messages;

    public QuestionService(
            QuestionRepository questionRepository,
            QuestionVersionRepository versionRepository,
            QuestionKnowledgeRepository knowledgeRepository,
            QuestionTagRepository tagRepository,
            QuestionUsageLedgerRepository ledgerRepository,
            QuestionSupportDao supportDao,
            QuestionHtmlSanitizer sanitizer,
            QuestionAssetResolver assetResolver,
            CatalogLookup catalogLookup,
            AuditRecorder auditRecorder,
            CurrentUserProvider currentUserProvider,
            IdempotentCommand idempotentCommand,
            QuestionUsageProvider usageProvider,
            QuestionMapper mapper,
            AppMessages messages) {
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.tagRepository = tagRepository;
        this.ledgerRepository = ledgerRepository;
        this.supportDao = supportDao;
        this.sanitizer = sanitizer;
        this.assetResolver = assetResolver;
        this.catalogLookup = catalogLookup;
        this.auditRecorder = auditRecorder;
        this.currentUserProvider = currentUserProvider;
        this.idempotentCommand = idempotentCommand;
        this.usageProvider = usageProvider;
        this.mapper = mapper;
        this.messages = messages;
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
        applySave(question, request, now, actorId);
        questionRepository.save(question);
        audit("QUESTION.CREATE", question, before, auditState(question));
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
        return new UpdateQuestionResponse(mapper.toDetail(question, mapper.batch()), versionCreated);
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

        Sanitized sanitized = sanitize(request);
        QuestionInput input = new QuestionInput(
                type, sanitized.body(), effectivePoints(type, request == null ? null : request.points(), sanitized.body()),
                sanitized.content(), sanitized.solution(), sanitized.commonMistakes(), sanitized.attachmentIds());
        List<QuestionFieldError> errors = QuestionBodyValidator.validateSave(input, assetResolver);
        if (!errors.isEmpty()) {
            throw bodyInvalid(errors);
        }

        boolean versionCreated = false;
        Integer targetVersion;
        if (question.getStatus() == QuestionStatus.DRAFT && question.getWorkingVersionNo() != null) {
            targetVersion = question.getWorkingVersionNo();
        } else {
            Integer max = versionRepository.findMaxVersionNo(question.getId());
            targetVersion = (max == null ? 0 : max) + 1;
            versionCreated = true;
        }

        // Câu con của bộ: đồng bộ dòng trước để bản chụp của bộ mang id câu con.
        if (type == QuestionType.QUESTION_SET
                && sanitized.body() instanceof QuestionBody.QuestionSetBody setBody) {
            syncChildren(question, setBody, classification, knowledge,
                    targetVersion, now, actorId);
        }

        QuestionSnapshot snapshot = new QuestionSnapshot(
                classification.subjectItemId(), classification.gradeItemId(),
                classification.levelItemId(), classification.taskTypeItemId(),
                classification.competencyItemId(), input.points(),
                toSnapshotKnowledge(knowledge), tagItemIds,
                sanitized.content(), sanitized.solution(), sanitized.commonMistakes(),
                sanitized.attachmentIds(), sanitized.body());
        upsertVersion(question.getId(), targetVersion, snapshot, actorId, now);

        question.setSubjectItemId(classification.subjectItemId());
        question.setGradeItemId(classification.gradeItemId());
        question.setLevelItemId(classification.levelItemId());
        question.setTaskTypeItemId(classification.taskTypeItemId());
        question.setCompetencyItemId(classification.competencyItemId());
        question.setPoints(input.points());
        question.setContentText(QuestionSearchNormalizer.contentText(sanitized.content(), sanitized.body()));
        question.setSearchText(QuestionSearchNormalizer.searchText(
                question.getCode(), sanitized.content(), sanitized.body()));
        question.setHasSolution(sanitized.solution() != null && !sanitized.solution().isBlank());
        setSourceCode(question, request == null ? null : request.sourceCode());
        question.setWorkingVersionNo(targetVersion);
        question.setStatus(QuestionStatus.DRAFT);
        question.touch(now, actorId);

        knowledgeRepository.deleteByQuestionId(question.getId());
        saveKnowledgeRows(question.getId(), knowledge);
        tagRepository.deleteByQuestionId(question.getId());
        for (UUID tagItemId : tagItemIds) {
            tagRepository.save(new QuestionTag(question.getId(), tagItemId));
        }
        return versionCreated;
    }

    // ---------------------------------------------------------------- trạng thái

    /** Đưa câu lên Sẵn sàng: kiểm đủ điều kiện (spec 4.5) rồi đóng bản làm việc. */
    @Transactional
    public QuestionSummary markReady(UUID id, Long expectedRevision) {
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, expectedRevision);
        switch (question.getStatus()) {
            case READY -> throw new ConflictException(ERR_ALREADY_READY);
            case RETIRED -> throw new ConflictException(ERR_ALREADY_RETIRED);
            default -> { /* DRAFT: tiếp tục */ }
        }
        QuestionVersion working = workingVersion(question);
        if (working == null || working.getSnapshot() == null) {
            throw new ConflictException(ERR_NOT_READY);
        }
        readinessOrThrow(question, working.getSnapshot());

        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
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
        supportDao.bumpSourceRevision(question.getSubjectItemId());
        audit("QUESTION.READY", question, null, auditState(question));
        return mapper.toSummary(question, working, mapper.batch());
    }

    /** Ngừng câu Sẵn sàng: bỏ bản Nháp đang sửa, phiên bản phục vụ giữ nguyên (spec 4.5). */
    @Transactional
    public QuestionSummary retire(UUID id, Long expectedRevision) {
        Question question = loadStandaloneForUpdate(id);
        checkRevision(question, expectedRevision);
        switch (question.getStatus()) {
            case RETIRED -> throw new ConflictException(ERR_ALREADY_RETIRED);
            case DRAFT -> throw new ConflictException(ERR_NOT_READY);
            default -> { /* READY: tiếp tục */ }
        }
        Instant now = Instant.now();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        if (question.getWorkingVersionNo() != null) {
            versionRepository.deleteById(
                    new QuestionVersion.PK(id, question.getWorkingVersionNo()));
            question.setWorkingVersionNo(null);
        }
        for (Question child : questionRepository.findByParentIdAndDeletedAtIsNullOrderByPosition(id)) {
            child.setStatus(QuestionStatus.RETIRED);
            child.touch(now, actorId);
        }
        question.setStatus(QuestionStatus.RETIRED);
        question.touch(now, actorId);
        questionRepository.save(question);
        supportDao.bumpSourceRevision(question.getSubjectItemId());
        audit("QUESTION.RETIRE", question, null, auditState(question));
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
            if (question.getStatus() == target) {
                skipped.add(new BulkQuestionStatusResult.Skipped(id, "ALREADY_IN_STATUS", null));
                continue;
            }
            if (target == QuestionStatus.READY && question.getStatus() == QuestionStatus.RETIRED) {
                skipped.add(new BulkQuestionStatusResult.Skipped(id, "NOT_READY", ERR_ALREADY_RETIRED));
                continue;
            }
            if (target == QuestionStatus.RETIRED && question.getStatus() == QuestionStatus.DRAFT) {
                skipped.add(new BulkQuestionStatusResult.Skipped(id, "NOT_READY", "QUESTION_DRAFT"));
                continue;
            }
            if (target == QuestionStatus.READY) {
                QuestionVersion working = workingVersion(question);
                QuestionFieldError firstError = working == null
                        ? new QuestionFieldError(ERR_NOT_READY, "status", ERR_NOT_READY)
                        : readinessErrors(question, working.getSnapshot());
                if (firstError != null) {
                    skipped.add(new BulkQuestionStatusResult.Skipped(
                            id, "NOT_READY", firstError.code()));
                    continue;
                }
                Instant now = Instant.now();
                UUID actorId = currentUserProvider.requireCurrentUser().id();
                working.setReadyAt(now);
                versionRepository.save(working);
                applyReadyToChildren(question, working.getVersionNo(), now, actorId);
                question.setCurrentVersionNo(working.getVersionNo());
                question.setWorkingVersionNo(null);
                question.setStatus(QuestionStatus.READY);
                question.touch(now, actorId);
            } else {
                Instant now = Instant.now();
                UUID actorId = currentUserProvider.requireCurrentUser().id();
                if (question.getWorkingVersionNo() != null) {
                    versionRepository.deleteById(
                            new QuestionVersion.PK(id, question.getWorkingVersionNo()));
                    question.setWorkingVersionNo(null);
                }
                applyRetireToChildren(question, now, actorId);
                question.setStatus(QuestionStatus.RETIRED);
                question.touch(now, actorId);
            }
            questionRepository.save(question);
            supportDao.bumpSourceRevision(question.getSubjectItemId());
            audit(target == QuestionStatus.READY ? "QUESTION.READY" : "QUESTION.RETIRE",
                    question, null, auditState(question));
            updated.add(mapper.toSummary(question, mapper.displayedVersion(question), mapper.batch()));
        }
        return new BulkQuestionStatusResult(updated, skipped);
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
        QuestionSnapshot copySnapshot = snapshot;
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
                            QuestionType.QUESTION_SET, setBody.passage(), copyChildren));
        }

        questionRepository.save(copy);
        applyRowFields(copy, copySnapshot, null, 1, now, actorId);
        versionRepository.save(new QuestionVersion(copy.getId(), 1, copySnapshot, actorId, now));
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
                persistChild(row, childSnapshot, 1, now, actorId);
            }
        }
        audit("QUESTION.DUPLICATE", copy, null, auditState(copy));
        return mapper.toDetail(copy, mapper.batch());
    }

    /** Xóa mềm câu độc lập; câu đã dùng (sổ hoặc nguồn bài) bị chặn 409 (spec 4.5). */
    @Transactional
    public void delete(UUID id, String idempotencyKey, Long expectedRevision) {
        idempotentCommand.execute(idempotencyKey, "question.delete", id, null, Void.class,
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
        if (!usageProvider.examUsagesOf(id).isEmpty()) {
            inUseLabels.add(question.getCode());
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
        if (question.getStatus() == QuestionStatus.DRAFT && question.getWorkingVersionNo() != null) {
            targetVersion = question.getWorkingVersionNo();
            versionCreated = false;
        } else {
            Integer max = versionRepository.findMaxVersionNo(id);
            targetVersion = (max == null ? 0 : max) + 1;
            versionCreated = true;
        }
        upsertVersion(id, targetVersion, snapshot, actorId, now);

        applyRowFields(question, snapshot, null, targetVersion, now, actorId);
        knowledgeRepository.deleteByQuestionId(id);
        saveKnowledgeRows(id, fromSnapshotKnowledge(snapshot.knowledge()));
        tagRepository.deleteByQuestionId(id);
        for (UUID tagItemId : snapshot.tagItemIds()) {
            tagRepository.save(new QuestionTag(id, tagItemId));
        }
        if (snapshot.body() instanceof QuestionBody.QuestionSetBody setBody) {
            restoreChildren(question, setBody, snapshot, targetVersion, now, actorId);
        }
        questionRepository.save(question);
        audit("QUESTION.RESTORE", question, before, auditState(question));
        return new UpdateQuestionResponse(mapper.toDetail(question, mapper.batch()), versionCreated);
    }

    // ---------------------------------------------------------------- câu con

    private record ChildPersisted(Question row, QuestionSnapshot snapshot) {}

    /**
     * Đồng bộ dòng câu con với {@code children[]} của thân: tạo câu con mới (id do máy chủ
     * hoặc client cung cấp), cập nhật câu con có sẵn, xóa mềm câu con bị bỏ ra khỏi bộ (chặn
     * khi câu con đã dùng — 409 {@code QUESTION_IN_USE}).
     */
    private List<ChildPersisted> syncChildren(
            Question parent,
            QuestionBody.QuestionSetBody setBody,
            ResolvedClassification parentClassification,
            List<KnowledgeResolved> parentKnowledge,
            int targetVersion,
            Instant now,
            UUID actorId) {
        List<QuestionBody.ChildQuestion> children = setBody.children();
        if (children.size() < CHILD_MIN) {
            throw new BusinessRuleException(ERR_BODY_INVALID, null,
                    List.of(new FieldError("body.children", "QUESTION_SET_CHILDREN_MIN", null)));
        }
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
        List<UUID> kept = new ArrayList<>(children.size());
        List<ChildPersisted> persisted = new ArrayList<>(children.size());
        List<UUID> parentKnowledgeIds = parentKnowledge.stream().map(KnowledgeResolved::itemId).toList();

        for (int i = 0; i < children.size(); i++) {
            QuestionBody.ChildQuestion child = children.get(i);
            UUID childId = child.id() != null ? child.id() : UUID.randomUUID();
            kept.add(childId);
            Question row = existing.get(childId);
            boolean isNew = row == null || row.getDeletedAt() != null;
            if (row == null) {
                row = Question.create(
                        childId, parent.getCode() + "." + (i + 1), child.type(),
                        parent.getId(), i, now, actorId);
            } else {
                if (row.getType() != child.type()) {
                    throw new BusinessRuleException(ERR_TYPE_IMMUTABLE, null,
                            List.of(new FieldError(
                                    "body.children[" + i + "].type", ERR_TYPE_IMMUTABLE, null)));
                }
                row.setDeletedAt(null);
                row.setPosition(i);
                row.setCode(parent.getCode() + "." + (i + 1));
            }
            UUID effectiveGrade = child.gradeItemId() != null
                    ? child.gradeItemId()
                    : parentClassification.gradeItemId();
            UUID effectiveLevel = child.levelItemId() != null
                    ? child.levelItemId()
                    : parentClassification.levelItemId();
            List<UUID> effectiveKnowledge = child.knowledgeItemIds().isEmpty()
                    ? parentKnowledgeIds
                    : child.knowledgeItemIds();
            QuestionSnapshot childSnapshot = new QuestionSnapshot(
                    parentClassification.subjectItemId(), effectiveGrade, effectiveLevel,
                    parentClassification.taskTypeItemId(), parentClassification.competencyItemId(),
                    child.points() != null ? child.points() : BigDecimal.ONE,
                    effectiveKnowledge.stream()
                            .map(k -> new QuestionSnapshot.KnowledgeRef(k, false)).toList(),
                    List.of(), child.content(), child.solution(),
                    List.of(), List.of(), child.body());

            applyChildRow(row, child, childSnapshot, isNew ? 1 : targetVersion,
                    parentClassification.subjectItemId(), now, actorId);
            upsertVersion(childId, isNew ? 1 : targetVersion, childSnapshot, actorId, now);
            questionRepository.save(row);
            knowledgeRepository.deleteByQuestionId(childId);
            saveKnowledgeIds(childId, effectiveKnowledge);
            persisted.add(new ChildPersisted(row, childSnapshot));
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
        return persisted;
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
                        childId, parent.getCode() + "." + (i + 1), child.type(),
                        parent.getId(), i, now, actorId);
            } else {
                row.setDeletedAt(null);
                row.setPosition(i);
                row.setCode(parent.getCode() + "." + (i + 1));
                row.setType(child.type());
            }
            // Bản chụp câu con mang giá trị đã quy hồi: chuyển lại thành ghi đè khi bằng của bộ.
            UUID gradeOverride = Objects.equals(child.gradeItemId(), parentSnapshot.gradeItemId())
                    ? null
                    : child.gradeItemId();
            UUID levelOverride = Objects.equals(child.levelItemId(), parentSnapshot.levelItemId())
                    ? null
                    : child.levelItemId();
            List<UUID> knowledgeIds = child.knowledgeItemIds();
            boolean sameAsParent = new HashSet<>(knowledgeIds).equals(new HashSet<>(parentKnowledgeIds));
            List<UUID> knowledgeOverride = sameAsParent ? List.of() : knowledgeIds;
            List<UUID> effectiveKnowledge = knowledgeIds.isEmpty() ? parentKnowledgeIds : knowledgeIds;

            QuestionSnapshot childSnapshot = new QuestionSnapshot(
                    parentSnapshot.subjectItemId(),
                    child.gradeItemId(), child.levelItemId(),
                    parentSnapshot.taskTypeItemId(), parentSnapshot.competencyItemId(),
                    child.points() != null ? child.points() : BigDecimal.ONE,
                    effectiveKnowledge.stream()
                            .map(k -> new QuestionSnapshot.KnowledgeRef(k, false)).toList(),
                    List.of(), child.content(), child.solution(),
                    List.of(), List.of(), child.body());
            applyChildRow(row, new QuestionBody.ChildQuestion(
                            child.type(), child.content(), child.body(), child.solution(),
                            child.points(), gradeOverride, levelOverride, knowledgeOverride, childId),
                    childSnapshot, isNew ? 1 : targetVersion,
                    parentSnapshot.subjectItemId(), now, actorId);
            upsertVersion(childId, isNew ? 1 : targetVersion, childSnapshot, actorId, now);
            questionRepository.save(row);
            knowledgeRepository.deleteByQuestionId(childId);
            saveKnowledgeIds(childId, effectiveKnowledge);
        }
        for (Map.Entry<UUID, Question> entry : existing.entrySet()) {
            Question leftover = entry.getValue();
            if (leftover.getDeletedAt() == null && !kept.contains(leftover.getId())) {
                leftover.setDeletedAt(now);
                leftover.touch(now, actorId);
            }
        }
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
        saveKnowledgeIds(row.getId(),
                snapshot.knowledge().stream().map(QuestionSnapshot.KnowledgeRef::itemId).toList());
    }

    private static QuestionSnapshot childSnapshot(
            QuestionBody.ChildQuestion child, QuestionSnapshot parent, BigDecimal parentPoints) {
        UUID grade = child.gradeItemId() != null ? child.gradeItemId() : parent.gradeItemId();
        UUID level = child.levelItemId() != null ? child.levelItemId() : parent.levelItemId();
        List<UUID> knowledgeIds = child.knowledgeItemIds().isEmpty()
                ? parent.knowledge().stream().map(QuestionSnapshot.KnowledgeRef::itemId).toList()
                : child.knowledgeItemIds();
        return new QuestionSnapshot(
                parent.subjectItemId(), grade, level,
                parent.taskTypeItemId(), parent.competencyItemId(),
                child.points() != null ? child.points() : BigDecimal.ONE,
                knowledgeIds.stream().map(k -> new QuestionSnapshot.KnowledgeRef(k, false)).toList(),
                List.of(), child.content(), child.solution(), List.of(), List.of(), child.body());
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
        QuestionHtmlSanitizer.Result result = sanitizer.clean(html);
        for (QuestionHtmlSanitizer.Violation v : result.violations()) {
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
                    b.type(), sanitizeOptions(b.options(), path + ".options", errors), b.shuffleOptions());
            case QuestionBody.MultipleChoiceBody b -> new QuestionBody.MultipleChoiceBody(
                    b.type(), sanitizeOptions(b.options(), path + ".options", errors),
                    b.shuffleOptions(), b.scoringRule());
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
                    b.pairs(), b.reuseRight(), b.shuffleLeft(), b.shuffleRight());
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

    private static String describe(QuestionHtmlSanitizer.Violation v) {
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
        QuestionInput input = new QuestionInput(
                question.getType(), snapshot.body(), snapshot.points(),
                snapshot.content(), snapshot.solution(), snapshot.commonMistakes(),
                snapshot.attachmentIds());
        var classification = new QuestionBodyValidator.ClassificationRequirement(
                snapshot.subjectItemId() != null,
                snapshot.gradeItemId() != null,
                snapshot.levelItemId() != null);
        boolean hasPrimary = snapshot.knowledge().stream().anyMatch(QuestionSnapshot.KnowledgeRef::primary);
        return QuestionBodyValidator.validateReady(input, classification, hasPrimary, assetResolver)
                .errors();
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
        question.setStatus(QuestionStatus.DRAFT);
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
        state.put("points", question.getPoints());
        return state;
    }
}
