package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.api.CreateExamRequest;
import vn.unicom.thi360.exam.api.DuplicateExamRequest;
import vn.unicom.thi360.exam.api.ExamCheckSummary;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamStructureResponse;
import vn.unicom.thi360.exam.api.ExamSummaryResponse;
import vn.unicom.thi360.exam.api.ExamVersionResponse;
import vn.unicom.thi360.exam.api.LoadExamTemplateRequest;
import vn.unicom.thi360.exam.api.PersonRef;
import vn.unicom.thi360.exam.api.SuggestExamCodeResponse;
import vn.unicom.thi360.exam.api.UpdateExamInfoRequest;
import vn.unicom.thi360.exam.api.UpdateExamStructureRequest;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector.CodeResolver;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector.ExamStructureSummary;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamCheckStatus;
import vn.unicom.thi360.exam.domain.ExamKindProfile;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamStatus;
import vn.unicom.thi360.exam.domain.ExamStructure;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamStructureOps;
import vn.unicom.thi360.exam.domain.ExamStructureValidator;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamStructureRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;
import vn.unicom.thi360.shared.web.LocaleConfig;

/** Bài thi: tạo theo phạm vi, thông tin, mẫu, cấu trúc, nhân bản, danh sách (spec 5.2, 9.2). */
@Service
public class ExamService {

    private static final String EXAM_GROUP = "EXAM";
    private static final String SUBJECT_GROUP = "SUBJECT";
    private static final String PART_GROUP = "EXAM_PART";
    private static final Set<String> SCOPES = Set.of("FULL", "PART", "SUBJECT");
    private static final Set<String> ALLOWED_SORT = Set.of(
            "code", "name", "status", "question_count", "duration_minutes", "max_score",
            "created_at", "updated_at");

    private final ExamRepository examRepository;
    private final ExamStructureRepository structureRepository;
    private final ExamBlockRefRepository blockRefRepository;
    private final ExamKindProfileRepository profileRepository;
    private final ExamTemplateService templateService;
    private final ExamBlockRefProjector blockRefProjector;
    private final ExamInstructionsPolicy instructionsPolicy;
    private final CatalogLookup catalogLookup;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;
    private final IdempotentCommand idempotentCommand;
    private final CurrentUserProvider currentUserProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AppMessages messages;

    public ExamService(
            ExamRepository examRepository,
            ExamStructureRepository structureRepository,
            ExamBlockRefRepository blockRefRepository,
            ExamKindProfileRepository profileRepository,
            ExamTemplateService templateService,
            ExamBlockRefProjector blockRefProjector,
            ExamInstructionsPolicy instructionsPolicy,
            CatalogLookup catalogLookup,
            UserDirectory userDirectory,
            AuditRecorder auditRecorder,
            IdempotentCommand idempotentCommand,
            CurrentUserProvider currentUserProvider,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AppMessages messages) {
        this.examRepository = examRepository;
        this.structureRepository = structureRepository;
        this.blockRefRepository = blockRefRepository;
        this.profileRepository = profileRepository;
        this.templateService = templateService;
        this.blockRefProjector = blockRefProjector;
        this.instructionsPolicy = instructionsPolicy;
        this.catalogLookup = catalogLookup;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
        this.idempotentCommand = idempotentCommand;
        this.currentUserProvider = currentUserProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    private ExamStructureCodec codec() {
        return new ExamStructureCodec(objectMapper);
    }

    // ================================================================ tra cứu

    @Transactional(readOnly = true)
    public PageResponse<ExamSummaryResponse> listExams(String q, String kind, String subject,
            String scope, String status, Boolean hasEssay, Boolean hasEnglishOption,
            PageQuery pageQuery) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (e.code ILIKE ? OR e.name ILIKE ?)");
            String like = "%" + escapeLike(q.trim()) + "%";
            params.add(like);
            params.add(like);
        }
        if (kind != null && !kind.isBlank()) {
            where.append(" AND k.code_normalized = ?");
            params.add(normalizeCode(kind));
        }
        if (subject != null && !subject.isBlank()) {
            where.append(" AND s.code_normalized = ?");
            params.add(normalizeCode(subject));
        }
        if (scope != null && !scope.isBlank()) {
            where.append(" AND e.scope = ?");
            params.add(scope.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND e.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (hasEssay != null) {
            where.append(" AND e.has_essay = ?");
            params.add(hasEssay);
        }
        if (hasEnglishOption != null) {
            where.append(" AND e.has_english_option = ?");
            params.add(hasEnglishOption);
        }

        String base = " FROM exam.exam e"
                + " LEFT JOIN catalog.catalog_item k ON k.id = e.kind_item_id"
                + " LEFT JOIN catalog.catalog_item s ON s.id = e.subject_item_id" + where;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + base, Long.class,
                params.toArray());
        String orderBy = orderClause(pageQuery);
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.id, e.code, e.name, e.scope, e.status, e.serving_version,"
                        + " e.has_unpublished_changes, e.question_count, e.has_essay,"
                        + " e.has_english_option, e.combination_count, e.duration_minutes,"
                        + " e.max_score, e.check_status, e.check_issue_count, e.checked_at,"
                        + " e.updated_at, e.updated_by, k.code AS kind_code,"
                        + " k.name_vi AS kind_vi, k.name_en AS kind_en" + base + orderBy
                        + " LIMIT ? OFFSET ?",
                dataParams.toArray());

        Map<UUID, PersonRef> people = peopleOf(rows);
        boolean english = isEnglish();
        List<ExamSummaryResponse> items = rows.stream()
                .map(r -> toSummary(r, people, english))
                .toList();
        int totalPages = total == null || total == 0 ? 0
                : (int) ((total + pageQuery.size() - 1) / pageQuery.size());
        return new PageResponse<>(items, pageQuery.page(), pageQuery.size(),
                total == null ? 0 : total, totalPages);
    }

    private String orderClause(PageQuery pageQuery) {
        StringBuilder sb = new StringBuilder(" ORDER BY ");
        var sort = pageQuery.sort();
        if (sort == null || sort.isUnsorted()) {
            return " ORDER BY e.updated_at DESC, e.id";
        }
        boolean first = true;
        for (var order : sort) {
            String column = order.getProperty().toLowerCase(Locale.ROOT);
            if (!ALLOWED_SORT.contains(column)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append("e.").append(column).append(order.isAscending() ? " ASC" : " DESC");
            first = false;
        }
        if (first) {
            sb.append("e.updated_at DESC");
        }
        sb.append(", e.id");
        return sb.toString();
    }

    private Map<UUID, PersonRef> peopleOf(List<Map<String, Object>> rows) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) {
            Object by = r.get("updated_by");
            if (by instanceof UUID id) {
                ids.add(id);
            }
        }
        Map<UUID, PersonRef> people = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            userDirectory.findByIds(ids).forEach((id, u) ->
                    people.put(id, new PersonRef(id, u.displayName())));
        }
        return people;
    }

    private ExamSummaryResponse toSummary(Map<String, Object> r, Map<UUID, PersonRef> people,
            boolean english) {
        String kindName = english && r.get("kind_en") != null && !((String) r.get("kind_en")).isBlank()
                ? (String) r.get("kind_en") : (String) r.get("kind_vi");
        UUID updatedBy = (UUID) r.get("updated_by");
        return new ExamSummaryResponse(
                (UUID) r.get("id"),
                (String) r.get("code"),
                (String) r.get("name"),
                (String) r.get("kind_code"),
                kindName,
                (String) r.get("scope"),
                (String) r.get("status"),
                (Integer) r.get("serving_version"),
                Boolean.TRUE.equals(r.get("has_unpublished_changes")),
                ((Number) r.get("question_count")).intValue(),
                Boolean.TRUE.equals(r.get("has_essay")),
                Boolean.TRUE.equals(r.get("has_english_option")),
                ((Number) r.get("combination_count")).intValue(),
                (Integer) r.get("duration_minutes"),
                (BigDecimal) r.get("max_score"),
                new ExamCheckSummary((String) r.get("check_status"),
                        ((Number) r.get("check_issue_count")).intValue(),
                        toInstant(r.get("checked_at"))),
                0,
                toInstant(r.get("updated_at")),
                people.get(updatedBy));
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        return null;
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse getExamDetail(UUID examId) {
        Exam exam = requireExam(examId);
        ExamStructureDocument doc = workingDocument(examId);
        return toDetail(exam, doc);
    }

    @Transactional(readOnly = true)
    public ExamStructureResponse getExamStructure(UUID examId) {
        Exam exam = requireExam(examId);
        ExamStructureDocument doc = workingDocument(examId);
        return new ExamStructureResponse(examId, doc.parts(), exam.getRevision());
    }

    /** Mã gợi ý <KIND>-<F|P|S>-[PHẦN|MÔN-]<seq:03d>: số tiếp theo chưa dùng trong cùng tiền tố. */
    @Transactional(readOnly = true)
    public SuggestExamCodeResponse suggestExamCode(String kindCode, String scope, String partCode,
            String subjectCode) {
        StringBuilder prefix = new StringBuilder();
        prefix.append(normalizeCode(kindCode).toUpperCase(Locale.ROOT)).append('-')
                .append(scopeLetter(scope)).append('-');
        if ("PART".equalsIgnoreCase(scope) && partCode != null && !partCode.isBlank()) {
            prefix.append(cleanSegment(partCode)).append('-');
        }
        if ("SUBJECT".equalsIgnoreCase(scope) && subjectCode != null && !subjectCode.isBlank()) {
            prefix.append(cleanSegment(subjectCode)).append('-');
        }
        String prefixStr = prefix.toString();
        Integer next = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(e.code_normalized FROM ?) AS bigint)), 0) + 1"
                        + " FROM exam.exam e"
                        + " WHERE LEFT(e.code_normalized, ?) = ?"
                        + " AND SUBSTRING(e.code_normalized FROM ?) ~ '^[0-9]{3,9}$'",
                Integer.class,
                prefixStr.length() + 1,
                prefixStr.length(),
                prefixStr.toLowerCase(Locale.ROOT),
                prefixStr.length() + 1);
        return new SuggestExamCodeResponse(prefixStr + String.format("%03d", next == null ? 1 : next));
    }

    private static String cleanSegment(String code) {
        return code.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static String scopeLetter(String scope) {
        if (scope == null) {
            return "F";
        }
        return switch (scope.trim().toUpperCase(Locale.ROOT)) {
            case "PART" -> "P";
            case "SUBJECT" -> "S";
            default -> "F";
        };
    }

    // ================================================================ lệnh ghi

    /** POST /exams — bắt buộc Idempotency-Key, phạm vi khóa exam.create. */
    @Transactional
    public ExamDetailResponse createExam(String idempotencyKey, CreateExamRequest request) {
        return idempotentCommand.execute(idempotencyKey, "exam.create", request,
                new com.fasterxml.jackson.core.type.TypeReference<ExamDetailResponse>() {},
                () -> doCreate(request));
    }

    private ExamDetailResponse doCreate(CreateExamRequest request) {
        Instant now = Instant.now();
        UUID actor = actorId();
        String code = requireCode(request.code());

        CatalogItemView kindItem = requireKindItem(request.kind());
        ExamKindProfile profile = profileRepository.findById(kindItem.id()).orElse(null);
        ScopeChoice scopeChoice = resolveScope(kindItem, profile, request.scope(),
                request.partCode(), request.subjectCode());

        ExamTemplate template = pickTemplate(request.templateCode(), kindItem.code(),
                scopeChoice.scope(), scopeChoice.partCode(), scopeChoice.subjectCode(), null);

        ExamStructureDocument doc;
        boolean edited;
        String templateCode = null;
        if (request.parts() != null && !request.parts().isEmpty()) {
            doc = ExamStructureOps.assignMissingIds(new ExamStructureDocument(request.parts()));
            ExamStructureValidator.validate(doc);
            edited = true;
            templateCode = template != null ? template.getCode() : null;
        } else if (template != null) {
            doc = ExamStructureOps.assignMissingIds(codec().read(template.getStructure()));
            ExamStructureValidator.validate(doc);
            edited = false;
            templateCode = template.getCode();
        } else {
            doc = new ExamStructureDocument(List.of());
            edited = false;
        }

        String timing = template != null && template.getTiming() != null
                ? template.getTiming() : profile != null ? profile.getTiming() : "PER_PART";
        Integer duration = request.durationMinutes() != null ? request.durationMinutes()
                : template != null ? template.getDurationMinutes() : null;
        requireSharedDuration(timing, duration);
        BigDecimal maxScore = request.maxScore() != null ? request.maxScore()
                : template != null && template.getMaxScore() != null ? template.getMaxScore()
                : profile != null ? profile.getMaxScore() : null;
        if (maxScore != null && maxScore.signum() <= 0) {
            throw new BusinessRuleException(ExamErrorCodes.MAX_SCORE_INVALID,
                    messages.get(ExamErrorCodes.MAX_SCORE_INVALID));
        }

        Integer structureYear = template != null ? template.getStructureYear() : null;
        Integer matrixYear = template != null ? template.getMatrixYear() : null;
        boolean yearsConfirmed = structureYear == null || structureYear.equals(matrixYear)
                || Boolean.TRUE.equals(request.yearsConfirmed());

        Exam exam = new Exam(Ids.newId(), code, request.name().trim(), kindItem.id(),
                scopeChoice.scope(), timing, duration, maxScore,
                profile != null ? profile.getPracticeScoring() : "RAW", now, actor);
        exam.updateInfo(code, request.name().trim(),
                blankToNull(request.description()), instructionsPolicy.check(request.instructions()),
                duration, maxScore, actor, now);
        exam.changeScope(kindItem.id(), scopeChoice.scope(), scopeChoice.partCode(),
                scopeChoice.subjectItemId(), structureYear, matrixYear, yearsConfirmed,
                templateCode, timing, duration, maxScore, actor, now);
        try {
            exam = examRepository.saveAndFlush(exam);
        } catch (DataIntegrityViolationException ex) {
            throw codeTaken();
        }
        ExamStructureSummary summary = blockRefProjector.summarize(doc.parts());
        exam.applyStructureSummary(summary.questionCount(), summary.hasEssay(),
                summary.hasEnglishOption(), summary.combinationCount());

        structureRepository.save(new ExamStructure(exam.getId(), codec().write(doc),
                hash(doc), null, edited, actor, now));
        rewriteBlockRefs(exam.getId(), doc);

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.CREATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .after(Map.of("kind", kindItem.code(),
                        "scope", scopeChoice.scope(),
                        "templateCode", templateCode == null ? "" : templateCode))
                .build());
        return toDetail(examRepository.findById(exam.getId()).orElse(exam), doc);
    }

    /** PATCH /exams/{id} — sửa thông tin; đổi phạm vi → nạp lại mẫu theo quy tắc spec 5.2. */
    @Transactional
    public ExamDetailResponse updateExamInfo(UUID examId, UpdateExamInfoRequest request) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> examNotFound(examId));
        requireRevision(request.expectedRevision(), exam.getRevision());
        Instant now = Instant.now();
        UUID actor = actorId();

        boolean scopeRequested = request.kind() != null || request.scope() != null
                || request.partCode() != null || request.subjectCode() != null
                || request.structureYear() != null || request.matrixYear() != null;
        boolean scopeChanged = false;
        if (scopeRequested) {
            CatalogItemView kindItem = request.kind() != null ? requireKindItem(request.kind())
                    : catalogLookup.findItem(exam.getKindItemId())
                            .orElseThrow(() -> examNotFound(examId));
            String scope = request.scope() != null ? request.scope().trim().toUpperCase(Locale.ROOT)
                    : exam.getScope();
            String partCode = request.partCode() != null ? request.partCode().trim() : exam.getPartCode();
            String subjectCode = request.subjectCode() != null ? request.subjectCode().trim()
                    : subjectCodeOf(exam.getSubjectItemId());
            Integer structureYear = request.structureYear() != null ? request.structureYear()
                    : exam.getStructureYear();
            Integer matrixYear = request.matrixYear() != null ? request.matrixYear()
                    : exam.getMatrixYear();
            scopeChanged = !Objects.equals(kindItem.id(), exam.getKindItemId())
                    || !Objects.equals(scope, exam.getScope())
                    || !Objects.equals(partCode, exam.getPartCode())
                    || !Objects.equals(subjectItemOf(subjectCode), exam.getSubjectItemId())
                    || !Objects.equals(structureYear, exam.getStructureYear())
                    || !Objects.equals(matrixYear, exam.getMatrixYear());
            if (scopeChanged) {
                if (exam.getServingVersion() != null) {
                    throw new ConflictException(ExamErrorCodes.SCOPE_LOCKED,
                            messages.get(ExamErrorCodes.SCOPE_LOCKED));
                }
                ExamStructure structure = structureRepository.findById(examId).orElse(null);
                if (structure != null && structure.isEdited()
                        && !Boolean.TRUE.equals(request.confirmReplace())) {
                    throw new ConflictException(ExamErrorCodes.REPLACE_CONFIRM,
                            messages.get(ExamErrorCodes.REPLACE_CONFIRM));
                }
                ExamKindProfile profile = profileRepository.findById(kindItem.id()).orElse(null);
                ScopeChoice choice = resolveScope(kindItem, profile, scope, partCode, subjectCode);
                ExamTemplate template = pickTemplate(null, kindItem.code(), choice.scope(),
                        choice.partCode(), choice.subjectCode(), structureYear);
                ExamStructureDocument doc = template != null
                        ? ExamStructureOps.assignMissingIds(codec().read(template.getStructure()))
                        : new ExamStructureDocument(List.of());
                ExamStructureValidator.validate(doc);
                exam.changeScope(kindItem.id(), choice.scope(), choice.partCode(),
                        choice.subjectItemId(),
                        template != null ? template.getStructureYear() : structureYear,
                        template != null ? template.getMatrixYear() : matrixYear,
                        template == null || template.getStructureYear() == null
                                || template.getStructureYear().equals(template.getMatrixYear()),
                        template != null ? template.getCode() : null,
                        template != null && template.getTiming() != null ? template.getTiming()
                                : exam.getTiming(),
                        template != null && template.getDurationMinutes() != null
                                ? template.getDurationMinutes() : exam.getDurationMinutes(),
                        template != null && template.getMaxScore() != null ? template.getMaxScore()
                                : exam.getMaxScore(),
                        actor, now);
                writeStructure(exam, doc, false, actor, now);
            }
        }

        String code = exam.getCode();
        if (request.code() != null && !request.code().isBlank()
                && !normalizeCode(request.code()).equals(exam.getCodeNormalized())) {
            code = requireCode(request.code());
        } else if (request.code() != null && !request.code().isBlank()) {
            code = request.code().trim();
        }
        exam.updateInfo(code,
                request.name() != null && !request.name().isBlank() ? request.name().trim() : exam.getName(),
                request.description() != null ? blankToNull(request.description()) : exam.getDescription(),
                request.instructions() != null ? instructionsPolicy.check(request.instructions())
                        : exam.getInstructions(),
                request.durationMinutes() != null ? request.durationMinutes() : exam.getDurationMinutes(),
                request.maxScore() != null ? request.maxScore() : exam.getMaxScore(),
                actor, now);
        if (request.maxScore() != null && request.maxScore().signum() <= 0) {
            throw new BusinessRuleException(ExamErrorCodes.MAX_SCORE_INVALID,
                    messages.get(ExamErrorCodes.MAX_SCORE_INVALID));
        }
        if (request.yearsConfirmed() != null && !scopeChanged) {
            exam.setYearsConfirmed(request.yearsConfirmed());
        }
        try {
            examRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw codeTaken();
        }

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.UPDATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .after(Map.of("scopeChanged", scopeChanged))
                .build());
        return getExamDetail(examId);
    }

    /** POST /exams/{id}/template — nạp lại cấu trúc từ nguyên mẫu; cần confirmReplace khi đã sửa. */
    @Transactional
    public ExamStructureResponse loadTemplate(UUID examId, LoadExamTemplateRequest request) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> examNotFound(examId));
        requireRevision(request.expectedRevision(), exam.getRevision());
        ExamStructure structure = structureRepository.findById(examId).orElse(null);
        if (structure != null && structure.isEdited()
                && !Boolean.TRUE.equals(request.confirmReplace())) {
            throw new ConflictException(ExamErrorCodes.REPLACE_CONFIRM,
                    messages.get(ExamErrorCodes.REPLACE_CONFIRM));
        }
        ExamTemplate template = templateService.findActive(request.templateCode());
        if (template == null) {
            throw new BusinessRuleException(ExamErrorCodes.TEMPLATE_MISMATCH,
                    messages.get(ExamErrorCodes.TEMPLATE_MISMATCH));
        }
        assertTemplateMatches(exam, template);
        Instant now = Instant.now();
        UUID actor = actorId();
        ExamStructureDocument doc = ExamStructureOps.assignMissingIds(codec().read(template.getStructure()));
        ExamStructureValidator.validate(doc);
        exam.applyTemplate(template.getCode(), template.getStructureYear(), template.getMatrixYear(),
                template.getTiming(), template.getDurationMinutes(), template.getMaxScore(), actor, now);
        writeStructure(exam, doc, false, actor, now);

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.TEMPLATE_LOAD")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .after(Map.of("templateCode", template.getCode()))
                .build());
        return new ExamStructureResponse(examId, doc.parts(), examRepository.findById(examId)
                .map(Exam::getRevision).orElse(0L));
    }

    /** PUT /exams/{id}/structure — thay toàn bộ cấu trúc bản làm việc. */
    @Transactional
    public ExamStructureResponse updateStructure(UUID examId, UpdateExamStructureRequest request) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> examNotFound(examId));
        requireRevision(request.expectedRevision(), exam.getRevision());
        Instant now = Instant.now();
        UUID actor = actorId();

        ExamStructureDocument incoming = ExamStructureOps.assignMissingIds(
                new ExamStructureDocument(request.parts()));
        ExamStructure stored = structureRepository.findById(examId).orElse(null);
        ExamStructureDocument storedDoc = stored == null ? null
                : codec().read(stored.getParts());
        ExamTemplate template = exam.getTemplateCode() == null ? null
                : templateService.findActive(exam.getTemplateCode());
        ExamStructureDocument templateDoc = template == null ? null
                : codec().read(template.getStructure());
        ExamStructureDocument doc = ExamStructureOps.restoreTemplateOwned(incoming, storedDoc,
                templateDoc);
        ExamStructureValidator.validate(doc);
        writeStructure(exam, doc, true, actor, now);

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.STRUCTURE_UPDATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .after(Map.of("partCount", doc.parts().size()))
                .build());
        return new ExamStructureResponse(examId, doc.parts(), examRepository.findById(examId)
                .map(Exam::getRevision).orElse(0L));
    }

    /** POST /exams/{id}/duplicate — nhân bản thành Nháp; id trong cấu trúc sinh lại hết. */
    @Transactional
    public ExamDetailResponse duplicateExam(String idempotencyKey, UUID examId,
            DuplicateExamRequest request) {
        return idempotentCommand.execute(idempotencyKey, "exam.duplicate", examId.toString(), request,
                new com.fasterxml.jackson.core.type.TypeReference<ExamDetailResponse>() {},
                () -> doDuplicate(examId, request));
    }

    private ExamDetailResponse doDuplicate(UUID examId, DuplicateExamRequest request) {
        Exam source = examRepository.findById(examId).orElseThrow(() -> examNotFound(examId));
        Instant now = Instant.now();
        UUID actor = actorId();
        String code = requireCode(request.code());

        Exam copy = new Exam(Ids.newId(), code, request.name().trim(), source.getKindItemId(),
                source.getScope(), source.getTiming(), source.getDurationMinutes(),
                source.getMaxScore(), source.getPracticeScoring(), now, actor);
        copy.copyDetailsFrom(source);
        try {
            copy = examRepository.saveAndFlush(copy);
        } catch (DataIntegrityViolationException ex) {
            throw codeTaken();
        }

        ExamStructure sourceStructure = structureRepository.findById(examId).orElse(null);
        ExamStructureDocument doc = sourceStructure == null
                ? new ExamStructureDocument(List.of())
                : ExamStructureOps.regenerateIds(codec().read(sourceStructure.getParts()));
        structureRepository.save(new ExamStructure(copy.getId(), codec().write(doc),
                hash(doc), null, false, actor, now));
        rewriteBlockRefs(copy.getId(), doc);

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.DUPLICATE")
                .target("EXAM", copy.getId().toString(), copy.getCode())
                .after(Map.of("sourceExamId", examId.toString()))
                .build());
        return toDetail(copy, doc);
    }

    // ================================================================ nội bộ

    private Exam requireExam(UUID examId) {
        return examRepository.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private ExamStructureDocument workingDocument(UUID examId) {
        return structureRepository.findById(examId)
                .map(s -> codec().read(s.getParts()))
                .orElse(new ExamStructureDocument(List.of()));
    }

    private void writeStructure(Exam exam, ExamStructureDocument doc, boolean edited,
            UUID actor, Instant now) {
        JsonNode json = codec().write(doc);
        String hash = hash(doc);
        ExamStructure structure = structureRepository.findById(exam.getId()).orElse(null);
        if (structure == null) {
            structureRepository.save(new ExamStructure(exam.getId(), json, hash, null, edited,
                    actor, now));
        } else {
            structure.replace(json, hash, edited, actor, now);
            structureRepository.save(structure);
        }
        ExamStructureSummary summary = blockRefProjector.summarize(doc.parts());
        exam.applyStructureSummary(summary.questionCount(), summary.hasEssay(),
                summary.hasEnglishOption(), summary.combinationCount());
        exam.markStructureChanged(actor, now);
        rewriteBlockRefs(exam.getId(), doc);
    }

    private void rewriteBlockRefs(UUID examId, ExamStructureDocument doc) {
        blockRefRepository.deleteByExamIdAndVersionNo(examId, null);
        List<vn.unicom.thi360.exam.domain.ExamBlockRef> rows =
                blockRefProjector.project(examId, doc.parts(), resolver());
        if (!rows.isEmpty()) {
            blockRefRepository.saveAll(rows);
        }
    }

    private CodeResolver resolver() {
        return new CodeResolver(
                code -> catalogLookup.findActiveItem(SUBJECT_GROUP, code).map(CatalogItemView::id).orElse(null),
                code -> catalogLookup.findActiveItem("TASK_TYPE", code).map(CatalogItemView::id).orElse(null),
                nodeId -> catalogLookup.findLeafIdsUnder(nodeId),
                code -> catalogLookup.findActiveItem("COMPETENCY", code).map(CatalogItemView::id).orElse(null),
                code -> catalogLookup.findActiveItem("SUBJECT_COMBINATION", code)
                        .map(CatalogItemView::id).orElse(null));
    }

    private String hash(ExamStructureDocument doc) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    md.digest(codec().writeString(doc).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record ScopeChoice(String scope, String partCode, String subjectCode,
            UUID subjectItemId) {
    }

    /**
     * Kiểm phạm vi theo hồ sơ kỳ thi: scope ∈ scopes, PART cần partCode (giá trị nhóm EXAM_PART),
     * SUBJECT hoặc mẫu theo môn cần subjectCode (giá trị nhóm SUBJECT đang hoạt động).
     */
    private ScopeChoice resolveScope(CatalogItemView kindItem, ExamKindProfile profile,
            String rawScope, String partCode, String subjectCode) {
        if (profile == null || profile.getScopes().isEmpty()) {
            throw new BusinessRuleException(ExamErrorCodes.SCOPE_NOT_ALLOWED,
                    messages.get(ExamErrorCodes.SCOPE_NOT_ALLOWED));
        }
        String scope = rawScope == null ? "" : rawScope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope) || !profile.getScopes().contains(scope)) {
            throw new BusinessRuleException(ExamErrorCodes.SCOPE_NOT_ALLOWED,
                    messages.get(ExamErrorCodes.SCOPE_NOT_ALLOWED));
        }
        String part = null;
        if ("PART".equals(scope)) {
            if (partCode == null || partCode.isBlank()) {
                throw new BusinessRuleException(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED,
                        messages.get(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED));
            }
            part = partCode.trim();
        }
        String subject = null;
        UUID subjectItemId = null;
        boolean subjectRequired = "SUBJECT".equals(scope)
                || (profile.getTemplateBy() != null && "SUBJECT".equals(profile.getTemplateBy()));
        if (subjectRequired) {
            if (subjectCode == null || subjectCode.isBlank()) {
                throw new BusinessRuleException(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED,
                        messages.get(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED));
            }
            CatalogItemView item = catalogLookup.findActiveItem(SUBJECT_GROUP, subjectCode.trim())
                    .orElseThrow(() -> new BusinessRuleException(
                            ExamErrorCodes.PART_OR_SUBJECT_REQUIRED,
                            messages.get(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED)));
            subject = item.code();
            subjectItemId = item.id();
        }
        return new ScopeChoice(scope, part, subject, subjectItemId);
    }

    private ExamTemplate pickTemplate(String templateCode, String kindCode, String scope,
            String partCode, String subjectCode, Integer structureYear) {
        if (templateCode != null && !templateCode.isBlank()) {
            ExamTemplate t = templateService.findActive(templateCode);
            if (t == null || !kindCode.equals(t.getKindCode()) || !scope.equals(t.getScope())
                    || ("PART".equals(scope) && !Objects.equals(partCode, t.getPartCode()))
                    || (t.getSubjectCode() != null && !Objects.equals(subjectCode, t.getSubjectCode()))) {
                throw new BusinessRuleException(ExamErrorCodes.TEMPLATE_MISMATCH,
                        messages.get(ExamErrorCodes.TEMPLATE_MISMATCH));
            }
            return t;
        }
        return templateService.findBestMatch(kindCode, scope, partCode, subjectCode, structureYear);
    }

    private void assertTemplateMatches(Exam exam, ExamTemplate template) {
        String kindCode = catalogLookup.findItem(exam.getKindItemId())
                .map(CatalogItemView::code).orElse(null);
        String subjectCode = subjectCodeOf(exam.getSubjectItemId());
        if (!Objects.equals(kindCode, template.getKindCode())
                || !Objects.equals(exam.getScope(), template.getScope())
                || (template.getPartCode() != null
                        && !Objects.equals(exam.getPartCode(), template.getPartCode()))
                || (template.getSubjectCode() != null
                        && !Objects.equals(subjectCode, template.getSubjectCode()))) {
            throw new BusinessRuleException(ExamErrorCodes.TEMPLATE_MISMATCH,
                    messages.get(ExamErrorCodes.TEMPLATE_MISMATCH));
        }
    }

    private ExamDetailResponse toDetail(Exam exam, ExamStructureDocument doc) {
        boolean english = isEnglish();
        String kindCode = null;
        String kindName = null;
        if (exam.getKindItemId() != null) {
            CatalogItemView item = catalogLookup.findItem(exam.getKindItemId()).orElse(null);
            if (item != null) {
                kindCode = item.code();
                kindName = english && item.nameEn() != null && !item.nameEn().isBlank()
                        ? item.nameEn() : item.nameVi();
            }
        }
        PersonRef updatedBy = exam.getUpdatedBy() == null ? null
                : userDirectory.findById(exam.getUpdatedBy())
                        .map(u -> new PersonRef(u.id(), u.displayName()))
                        .orElseGet(() -> new PersonRef(exam.getUpdatedBy(), null));
        List<ExamVersionResponse> versions = new ArrayList<>();
        versions.add(new ExamVersionResponse(null, "WORKING", null, null, 0, null));
        return new ExamDetailResponse(
                exam.getId(),
                exam.getCode(),
                exam.getName(),
                kindCode,
                kindName,
                exam.getScope(),
                exam.getPartCode(),
                subjectCodeOf(exam.getSubjectItemId()),
                exam.getStatus(),
                exam.getServingVersion(),
                exam.isHasUnpublishedChanges(),
                exam.getQuestionCount(),
                exam.isHasEssay(),
                exam.isHasEnglishOption(),
                exam.getCombinationCount(),
                exam.getTiming(),
                exam.getDurationMinutes(),
                exam.getMaxScore(),
                exam.getReferenceMax(),
                exam.getPracticeScoring(),
                exam.getTemplateCode(),
                exam.getStructureYear(),
                exam.getMatrixYear(),
                exam.isYearsConfirmed(),
                new ExamCheckSummary(exam.getCheckStatus(), exam.getCheckIssueCount(),
                        exam.getCheckedAt()),
                0,
                exam.getUpdatedAt(),
                updatedBy,
                exam.getDescription(),
                exam.getInstructions(),
                doc.parts(),
                versions,
                exam.getRevision() == null ? 0L : exam.getRevision());
    }

    private String subjectCodeOf(UUID subjectItemId) {
        if (subjectItemId == null) {
            return null;
        }
        return catalogLookup.findItem(subjectItemId).map(CatalogItemView::code).orElse(null);
    }

    private UUID subjectItemOf(String subjectCode) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return null;
        }
        return catalogLookup.findActiveItem(SUBJECT_GROUP, subjectCode.trim())
                .map(CatalogItemView::id).orElse(null);
    }

    private CatalogItemView requireKindItem(String kindCode) {
        if (kindCode == null || kindCode.isBlank()) {
            throw new BusinessRuleException(ExamErrorCodes.KIND_UNKNOWN,
                    messages.get(ExamErrorCodes.KIND_UNKNOWN));
        }
        return catalogLookup.findActiveItem(EXAM_GROUP, kindCode.trim())
                .orElseThrow(() -> new BusinessRuleException(ExamErrorCodes.KIND_UNKNOWN,
                        messages.get(ExamErrorCodes.KIND_UNKNOWN)));
    }

    private void requireSharedDuration(String timing, Integer duration) {
        if ("SHARED".equals(timing) && (duration == null || duration <= 0)) {
            throw new ValidationException(List.of(new FieldError("durationMinutes",
                    "EXAM_DURATION_REQUIRED", messages.get("validation.exam.duration.shared"))));
        }
    }

    private void requireRevision(Long expected, Long current) {
        long cur = current == null ? 0L : current;
        if (expected == null || expected != cur) {
            throw new VersionConflictException(ExamErrorCodes.REVISION_CONFLICT,
                    messages.get(ExamErrorCodes.REVISION_CONFLICT));
        }
    }

    private String requireCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) {
            throw new ValidationException(List.of(new FieldError("code", "REQUIRED",
                    messages.get("validation.required"))));
        }
        examRepository.findByCodeNormalized(normalizeCode(code)).ifPresent(existing -> {
            throw codeTaken();
        });
        return code;
    }

    private ConflictException codeTaken() {
        return new ConflictException(ExamErrorCodes.CODE_TAKEN,
                messages.get(ExamErrorCodes.CODE_TAKEN));
    }

    private NotFoundException examNotFound(UUID id) {
        return new NotFoundException(messages.get("error.notFound", "EXAM", id));
    }

    private UUID actorId() {
        return currentUserProvider.getCurrentUser().map(u -> u.id()).orElse(null);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isEnglish() {
        return Locale.ENGLISH.getLanguage()
                .equalsIgnoreCase(LocaleConfig.currentLocale().getLanguage());
    }
}
