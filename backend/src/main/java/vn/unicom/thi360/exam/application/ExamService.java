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
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.exam.api.CreateExamRequest;
import vn.unicom.thi360.exam.api.DuplicateExamRequest;
import vn.unicom.thi360.exam.api.ExamCheckSummary;
import vn.unicom.thi360.exam.api.ExamDetailResponse;
import vn.unicom.thi360.exam.api.ExamOverviewRequest;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamStructureResponse;
import vn.unicom.thi360.exam.api.ExamSummaryResponse;
import vn.unicom.thi360.exam.api.ExamVersionResponse;
import vn.unicom.thi360.exam.api.LoadExamTemplateRequest;
import vn.unicom.thi360.exam.api.PersonRef;
import vn.unicom.thi360.exam.api.SuggestExamCodeResponse;
import vn.unicom.thi360.exam.api.StructureCombination;
import vn.unicom.thi360.exam.api.UpdateExamInfoRequest;
import vn.unicom.thi360.exam.api.UpdateExamStructureRequest;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector.CodeResolver;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector.ExamStructureSummary;
import vn.unicom.thi360.exam.domain.BlockMatrix;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamCheckStatus;
import vn.unicom.thi360.exam.domain.ExamKindProfile;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStatus;
import vn.unicom.thi360.exam.domain.ExamStructure;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamStructureOps;
import vn.unicom.thi360.exam.domain.ExamStructureValidator;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamStructureProperties;
import vn.unicom.thi360.exam.infrastructure.ExamStructureRepository;
import vn.unicom.thi360.exam.spi.CombinationLookup;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.BusinessRuleException;
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
    private final ExamCheckValidity checkValidity;
    private final ExamStructureProperties structureProperties;
    private final CombinationLookup combinationLookup;
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
            AppMessages messages,
            ExamCheckValidity checkValidity,
            ExamStructureProperties structureProperties,
            CombinationLookup combinationLookup) {
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
        this.checkValidity = checkValidity;
        this.structureProperties = structureProperties;
        this.combinationLookup = combinationLookup;
    }
    private ExamStructureCodec codec() {
        return new ExamStructureCodec(objectMapper);
    }

    // ================================================================ tra cứu

    @Transactional(readOnly = true)
    public PageResponse<ExamSummaryResponse> listExams(String q, String kind, String subject,
            String scope, String status, Boolean hasEssay, Boolean hasEnglishOption,
            String checkStatus, Boolean hasUnpublishedChanges, PageQuery pageQuery) {
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
        if (hasUnpublishedChanges != null) {
            where.append(" AND e.has_unpublished_changes = ?");
            params.add(hasUnpublishedChanges);
        }

        String joins = " FROM exam.exam e"
                + " LEFT JOIN catalog.catalog_item k ON k.id = e.kind_item_id"
                + " LEFT JOIN catalog.catalog_item s ON s.id = e.subject_item_id";
        if (checkStatus != null && !checkStatus.isBlank()) {
            String wanted = checkStatus.trim().toUpperCase(Locale.ROOT);
            try {
                ExamCheckStatus.valueOf(wanted);
            } catch (IllegalArgumentException ex) {
                throw new ValidationException(List.of(new FieldError("checkStatus",
                        "INVALID_CHECK_STATUS", messages.get("validation.exam.checkStatus.invalid"))));
            }
            // Trạng thái hiệu lực làm vị từ DB (spec 5.4, B5): cùng phép so như
            // ExamCheckValidity.assess — hash cấu trúc, snapshot cấu hình, revision profile,
            // revision danh mục và từng source revision đã lưu. Count và trang đều dùng
            // predicate này nên trang nhỏ không vật liệu hóa danh sách id trong Java.
            joins += " LEFT JOIN exam.exam_structure es ON es.exam_id = e.id"
                    + " LEFT JOIN exam.exam_kind_profile kp ON kp.exam_item_id = e.kind_item_id"
                    + " LEFT JOIN exam.exam_check_run cr ON cr.exam_id = e.id";
            where.append(" AND (CASE WHEN cr.id IS NULL"
                    + " THEN CASE WHEN e.check_status = 'UNCHECKED' THEN 'UNCHECKED'"
                    + " ELSE 'NEEDS_RECHECK' END"
                    + " WHEN es.structure_hash IS NULL"
                    + " OR es.structure_hash IS DISTINCT FROM cr.structure_hash"
                    + " THEN 'NEEDS_RECHECK'"
                    + " WHEN NOT (cr.config_snapshot = jsonb_build_object("
                    + "'kindItemId', e.kind_item_id, 'scope', e.scope, 'partCode', e.part_code,"
                    + " 'subjectItemId', e.subject_item_id, 'templateCode', e.template_code,"
                    + " 'structureYear', e.structure_year, 'matrixYear', e.matrix_year,"
                    + " 'yearsConfirmed', e.years_confirmed, 'timing', e.timing,"
                    + " 'durationMinutes', e.duration_minutes, 'maxScore', e.max_score,"
                    + " 'referenceMax', e.reference_max, 'practiceScoring', e.practice_scoring))"
                    + " OR cr.profile_revision IS DISTINCT FROM kp.revision"
                    + " OR cr.catalog_revision IS DISTINCT FROM ?"
                    + " THEN 'NEEDS_RECHECK'"
                    + " WHEN EXISTS (SELECT 1 FROM jsonb_each_text(cr.source_revisions) sr"
                    + " LEFT JOIN questionbank.question_source_revision qs"
                    + " ON qs.subject_item_id = sr.key::uuid"
                    + " WHERE sr.value::bigint IS DISTINCT FROM COALESCE(qs.revision, 0))"
                    + " THEN 'NEEDS_RECHECK'"
                    + " ELSE cr.result->>'status' END) = ?");
            params.add(catalogLookup.revision());
            params.add(wanted);
        }

        String base = joins + where;
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
                        + " e.updated_at, e.updated_by, e.part_code, s.code AS subject_code,"
                        + " e.timing, e.reference_max, e.practice_scoring, e.revision,"
                        + " k.code AS kind_code,"
                        + " k.name_vi AS kind_vi, k.name_en AS kind_en" + base + orderBy
                        + " LIMIT ? OFFSET ?",
                dataParams.toArray());

        Map<UUID, PersonRef> people = peopleOf(rows);
        Map<UUID, String> checkStatuses = checkValidity.statuses(
                rows.stream().map(r -> (UUID) r.get("id")).toList());
        boolean english = isEnglish();
        List<ExamSummaryResponse> items = rows.stream()
                .map(r -> toSummary(r, people, english, checkStatuses.get((UUID) r.get("id"))))
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
            boolean english, String checkStatus) {
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
                (String) r.get("part_code"),
                (String) r.get("subject_code"),
                (String) r.get("status"),
                (Integer) r.get("serving_version"),
                Boolean.TRUE.equals(r.get("has_unpublished_changes")),
                ((Number) r.get("question_count")).intValue(),
                Boolean.TRUE.equals(r.get("has_essay")),
                Boolean.TRUE.equals(r.get("has_english_option")),
                ((Number) r.get("combination_count")).intValue(),
                (String) r.get("timing"),
                "SHARED".equals(r.get("timing")) ? (Integer) r.get("duration_minutes") : null,
                (BigDecimal) r.get("max_score"),
                (BigDecimal) r.get("reference_max"),
                (String) r.get("practice_scoring"),
                new ExamCheckSummary(checkStatus,
                        ((Number) r.get("check_issue_count")).intValue(),
                        toInstant(r.get("checked_at"))),
                0,
                toInstant(r.get("updated_at")),
                people.get(updatedBy),
                ((Number) r.get("revision")).longValue());
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
        return structureResponse(examId, doc, exam.getRevision());
    }

    private ExamStructureResponse structureResponse(UUID examId, ExamStructureDocument doc, long revision) {
        ExamStructureDocument presented = ExamStructurePresentation.withPositionRanges(doc);
        Exam exam = requireExam(examId);
        List<CatalogCombinationView> current = combinationLookup.listCombinations(exam.getKindItemId());
        List<StructureCombination> combinations = new ArrayList<>();
        for (ExamPart part : doc.parts()) {
            if (part.codeSource() == null) {
                continue;
            }
            String branch = part.codeSource().branch();
            Set<String> subjects = new java.util.HashSet<>();
            part.subjectPool().forEach(section -> subjects.add(section.subjectCode()));
            for (CatalogCombinationView value : current) {
                if (branch != null && (value.branch() == null || !branch.equals(value.branch().name()))) {
                    continue;
                }
                combinations.add(new StructureCombination(part.id(), value.itemCode(), value.itemNameVi(), value.itemNameEn(),
                        value.examItemCode(), value.subjectCodes(),
                        value.subjectCodes().stream().filter(code -> !subjects.contains(code)).toList()));
            }
        }
        return new ExamStructureResponse(examId, presented.parts(), revision, presented.examQuotas(), combinations, null);
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

        String buildMode = request.buildMode() == null ? "STANDARD" : request.buildMode();
        if (!Set.of("STANDARD", "REVIEW").contains(buildMode)
                || ("REVIEW".equals(buildMode) && !"SUBJECT".equals(scopeChoice.scope()))) {
            throw new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
        }
        boolean subjectPart = "PART".equals(scopeChoice.scope())
                && "SUBJECT".equals(profile.getTemplateBy());
        ExamTemplate template = subjectPart
                ? templateService.findDefault(kindItem.code(), "SUBJECT", null, scopeChoice.subjectCode(), buildMode)
                : templateService.findDefault(kindItem.code(), scopeChoice.scope(),
                        scopeChoice.partCode(), scopeChoice.subjectCode(), buildMode);

        ExamStructureDocument doc;
        String templateCode = null;
        if (template != null) {
            doc = ExamStructureOps.assignMissingIds(codec().read(template.getStructure()));
            if ("REVIEW".equals(buildMode)) {
                doc = reviewSubject(doc, scopeChoice.subjectCode());
            }
            if (subjectPart) {
                doc = selectSubjectPart(doc, scopeChoice.partCode());
            }
            ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
            templateCode = template.getCode();
        } else {
            doc = emptyOverviewStructure(request.overview(), scopeChoice.subjectCode());
            if (subjectPart) {
                throw new BusinessRuleException(ExamErrorCodes.PART_NOT_FOUND,
                        messages.get(ExamErrorCodes.PART_NOT_FOUND));
            }
        }
        if (template != null && request.overview() != null) {
            doc = ExamOverview.apply(doc, request.overview());
            ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
        }
        if (template == null) {
            ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
        }

        String timing = template != null && template.getTiming() != null
                ? template.getTiming() : profile != null ? profile.getTiming() : "PER_PART";
        Integer duration = request.durationMinutes() != null ? request.durationMinutes()
                : subjectPart ? doc.parts().getFirst().durationMinutes()
                : template != null ? template.getDurationMinutes() : null;
        requireSharedDuration(timing, duration);
        BigDecimal maxScore = request.maxScore() != null ? request.maxScore()
                : subjectPart ? doc.parts().getFirst().maxPoints()
                : template != null && template.getMaxScore() != null ? template.getMaxScore()
                : profile != null ? profile.getMaxScore() : null;
        if (maxScore != null && maxScore.signum() <= 0) {
            throw new BusinessRuleException(ExamErrorCodes.MAX_SCORE_INVALID,
                    messages.get(ExamErrorCodes.MAX_SCORE_INVALID));
        }

        Integer structureYear = template != null ? template.getStructureYear() : null;
        Integer matrixYear = template != null ? template.getMatrixYear() : null;
        boolean yearsConfirmed = structureYear == null || structureYear.equals(matrixYear);

        Exam exam = new Exam(Ids.newId(), code, request.name().trim(), kindItem.id(),
                scopeChoice.scope(), timing, duration, maxScore,
                profile != null ? profile.getPracticeScoring() : "RAW", now, actor);
        exam.setBuildMode(buildMode);
        exam.applyScoringProfile(subjectPart ? null : profile.getReferenceMax(),
                subjectPart ? "RAW" : profile.getPracticeScoring());
        exam.updateInfo(code, request.name().trim(),
                blankToNull(request.description()), instructionsPolicy.check(request.instructions()),
                duration, maxScore, actor, now);
        exam.changeScope(kindItem.id(), scopeChoice.scope(), scopeChoice.partCode(),
                scopeChoice.subjectItemId(), structureYear, matrixYear, yearsConfirmed,
                templateCode, timing, duration, maxScore, actor, now);
        exam.markStructureChanged(actor, now);
        try {
            exam = examRepository.saveAndFlush(exam);
        } catch (DataIntegrityViolationException ex) {
            throw codeTaken();
        }
        ExamStructureSummary summary = summarize(exam, doc);
        exam.applyStructureSummary(summary.questionCount(), summary.hasEssay(),
                summary.hasEnglishOption(), summary.combinationCount());

        structureRepository.save(new ExamStructure(exam.getId(), codec().writeParts(doc),
                codec().writeQuotas(doc.examQuotas()), hash(doc), null, false, actor, now));
        rewriteBlockRefs(exam.getId(), doc);

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.CREATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .after(Map.of("kind", kindItem.code(),
                        "scope", scopeChoice.scope(),
                        "templateCode", templateCode == null ? "" : templateCode))
                .build());
        return toDetail(examRepository.findById(exam.getId()).orElse(exam), doc);
    }

    private ExamStructureDocument emptyOverviewStructure(List<ExamOverviewRequest.Row> rows, String subjectCode) {
        if (rows == null) return new ExamStructureDocument(List.of());
        List<ExamPart> parts = new ArrayList<>();
        for (ExamOverviewRequest.Row row : rows) {
            String subject = row.subjectCode() != null ? row.subjectCode() : subjectCode;
            List<ExamSection> sections = subject == null ? List.of()
                    : List.of(new ExamSection(row.sectionId(), subject, row.questionCount(),
                            null, null, null, null, List.of()));
            parts.add(new ExamPart(row.partId(), null, row.partCode(), row.durationMinutes(),
                    row.questionCount(), null, null, null, row.maxPoints(), sections, List.of()));
        }
        return ExamStructureOps.assignMissingIds(new ExamStructureDocument(parts));
    }

    private ExamStructureDocument selectSubjectPart(ExamStructureDocument doc, String partCode) {
        List<ExamPart> parts = doc.parts().stream()
                .filter(p -> p.order() != null && ("P" + p.order()).equals(partCode))
                .toList();
        if (parts.size() != 1) {
            throw new BusinessRuleException(ExamErrorCodes.PART_NOT_FOUND,
                    messages.get(ExamErrorCodes.PART_NOT_FOUND));
        }
        return new ExamStructureDocument(parts);
    }

    private ExamStructureDocument reviewSubject(ExamStructureDocument doc, String subjectCode) {
        return new ExamStructureDocument(doc.parts().stream().map(part -> new ExamPart(
                part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                part.questionCountOnPaper(), part.questionCountRequired(), part.participation(),
                part.maxPoints(), part.sections().stream().map(section -> new ExamSection(
                        section.id(), subjectCode, section.questionCount(), section.knowledgeScope(),
                        section.answerQuotas(), section.matrix(), section.choiceGroup(), section.blocks(),
                        section.topicQuotas())).toList(), part.options(), part.subjectPool(),
                part.codeSource())).toList(), doc.examQuotas());
    }

    /** PATCH /exams/{id} — sửa thông tin; đổi phạm vi → nạp lại mẫu theo quy tắc spec 5.2. */
    @Transactional
    public ExamDetailResponse updateExamInfo(UUID examId, UpdateExamInfoRequest request) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> examNotFound(examId));
        requireRevision(request.expectedRevision(), exam.getRevision());
        if (request.practiceScoring() != null && !Set.of("RAW", "LINEAR").contains(request.practiceScoring())) {
            throw new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
        }
        if ("LINEAR".equals(request.practiceScoring()) && !"LINEAR".equals(exam.getPracticeScoring())
                && !Boolean.TRUE.equals(request.confirmScoring())) {
            throw new BusinessRuleException(ExamErrorCodes.SCORING_CONFIRM_REQUIRED,
                    messages.get(ExamErrorCodes.SCORING_CONFIRM_REQUIRED));
        }
        Map<String, Object> before = examInfoSnapshot(exam);
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
                String mode = "SUBJECT".equals(choice.scope()) ? exam.getBuildMode() : "STANDARD";
                boolean subjectPart = "PART".equals(choice.scope()) && "SUBJECT".equals(profile.getTemplateBy());
                ExamTemplate template = subjectPart
                        ? templateService.findDefault(kindItem.code(), "SUBJECT", null, choice.subjectCode(), mode)
                        : templateService.findDefault(kindItem.code(), choice.scope(), choice.partCode(),
                                choice.subjectCode(), mode);
                if (subjectPart && template == null) {
                    throw new BusinessRuleException(ExamErrorCodes.PART_NOT_FOUND,
                            messages.get(ExamErrorCodes.PART_NOT_FOUND));
                }
                ExamStructureDocument doc = template != null
                        ? ExamStructureOps.assignMissingIds(codec().read(template.getStructure()))
                        : new ExamStructureDocument(List.of());
                if (template != null && "REVIEW".equals(mode)) doc = reviewSubject(doc, choice.subjectCode());
                if (subjectPart) doc = selectSubjectPart(doc, choice.partCode());
                ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
                String timing = template != null && template.getTiming() != null
                        ? template.getTiming() : profile.getTiming();
                Integer duration = request.durationMinutes() != null ? request.durationMinutes()
                        : subjectPart ? doc.parts().getFirst().durationMinutes()
                        : template != null && template.getDurationMinutes() != null
                                ? template.getDurationMinutes() : null;
                BigDecimal maxScore = subjectPart ? doc.parts().getFirst().maxPoints()
                        : template != null && template.getMaxScore() != null ? template.getMaxScore() : profile.getMaxScore();
                requireSharedDuration(timing, duration);
                exam.applyScoringProfile(subjectPart ? null : profile.getReferenceMax(),
                        subjectPart ? "RAW" : profile.getPracticeScoring());
                exam.setBuildMode(mode);
                exam.changeScope(kindItem.id(), choice.scope(), choice.partCode(),
                        choice.subjectItemId(),
                        template != null ? template.getStructureYear() : structureYear,
                        template != null ? template.getMatrixYear() : matrixYear,
                        template == null || template.getStructureYear() == null
                                || template.getStructureYear().equals(template.getMatrixYear()),
                        template != null ? template.getCode() : null,
                        timing, duration, maxScore,
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
        if (request.practiceScoring() != null || request.referenceMax() != null) {
            String scoring = request.practiceScoring() == null
                    ? exam.getPracticeScoring() : request.practiceScoring();
            BigDecimal referenceMax = request.referenceMax() != null ? request.referenceMax()
                    : exam.getReferenceMax() != null ? exam.getReferenceMax()
                    : "LINEAR".equals(scoring) ? BigDecimal.TEN : null;
            exam.applyScoringProfile(referenceMax, scoring);
        }
        exam.markStructureChanged(actor, now);
        try {
            examRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw codeTaken();
        }

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.UPDATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .before(before)
                .after(examInfoSnapshot(exam))
                .build());
        return getExamDetail(examId);
    }

    /** Update overview numbers without replacing configured blocks or quotas. */
    @Transactional
    public ExamDetailResponse updateOverview(UUID examId, ExamOverviewRequest request) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> examNotFound(examId));
        requireRevision(request.expectedRevision(), exam.getRevision());
        ExamStructure stored = structureRepository.findById(examId).orElse(null);
        ExamStructureDocument doc = ExamOverview.apply(workingDocument(examId), request.overview());
        ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
        writeStructure(exam, doc, stored != null && stored.isEdited(), actorId(), Instant.now());
        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.OVERVIEW_UPDATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .build());
        return toDetail(exam, doc);
    }

    /** POST /exams/{id}/template — nạp lại cấu trúc từ nguyên mẫu; cần confirmReplace khi đã sửa. */
    @Transactional
    public ExamStructureResponse loadTemplate(UUID examId, LoadExamTemplateRequest request) {
        Exam exam = examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> examNotFound(examId));
        requireRevision(request.expectedRevision(), exam.getRevision());
        ExamStructure structure = structureRepository.findById(examId).orElse(null);
        if (!Boolean.TRUE.equals(request.confirmReplace())) {
            throw new ConflictException(ExamErrorCodes.REPLACE_CONFIRM,
                    messages.get(ExamErrorCodes.REPLACE_CONFIRM));
        }
        ExamTemplate template = templateService.findActive(request.templateCode());
        if (template == null) {
            throw new BusinessRuleException(ExamErrorCodes.TEMPLATE_MISMATCH,
                    messages.get(ExamErrorCodes.TEMPLATE_MISMATCH));
        }
        assertTemplateMatches(exam, template);
        boolean modeChanged = !Objects.equals(exam.getBuildMode(), template.getBuildMode());
        if (modeChanged && !Boolean.TRUE.equals(request.confirmReplace())) {
            throw new ConflictException(ExamErrorCodes.REPLACE_CONFIRM,
                    messages.get(ExamErrorCodes.REPLACE_CONFIRM));
        }
        if (request.overviewPolicy() != null
                && !Set.of("USE_TEMPLATE", "KEEP_EDITED").contains(request.overviewPolicy())) {
            throw new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
        }
        Instant now = Instant.now();
        UUID actor = actorId();
        ExamStructureDocument doc = ExamStructureOps.assignMissingIds(codec().read(template.getStructure()));
        boolean subjectPart = "PART".equals(exam.getScope()) && "SUBJECT".equals(template.getScope());
        if (subjectPart) {
            doc = selectSubjectPart(doc, exam.getPartCode());
        }
        if ("REVIEW".equals(template.getBuildMode())) {
            doc = reviewSubject(doc, subjectCodeOf(exam.getSubjectItemId()));
        }
        ExamTemplate previous = templateService.findActive(exam.getTemplateCode());
        if (previous != null && structure != null) {
            ExamStructureDocument baseline = codec().read(previous.getStructure());
            if (subjectPart) {
                baseline = selectSubjectPart(baseline, exam.getPartCode());
            }
            ExamStructureDocument current = readStoredStructure(structure);
            List<ExamOverviewRequest.Row> changes = ExamOverview.changes(current, baseline);
            if (!changes.isEmpty() && request.overviewPolicy() == null) {
                throw new ConflictException(ExamErrorCodes.OVERVIEW_CONFIRM,
                        messages.get(ExamErrorCodes.OVERVIEW_CONFIRM), List.of(
                                new FieldError("overview.current", "CURRENT",
                                        objectMapper.valueToTree(ExamOverview.rows(readStoredStructure(structure))).toString()),
                                new FieldError("overview.template", "TEMPLATE",
                                        objectMapper.valueToTree(ExamOverview.rows(doc)).toString())));
            }
            if ("KEEP_EDITED".equals(request.overviewPolicy())) {
                doc = ExamOverview.apply(doc, ExamOverview.presentIn(current, doc, changes));
            }
        }
        ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
        exam.applyTemplate(template.getCode(), template.getStructureYear(), template.getMatrixYear(),
                template.getTiming(), subjectPart ? doc.parts().getFirst().durationMinutes() : template.getDurationMinutes(),
                subjectPart ? doc.parts().getFirst().maxPoints() : template.getMaxScore(), actor, now);
        exam.setBuildMode(template.getBuildMode());
        writeStructure(exam, doc, false, actor, now);

        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.TEMPLATE_LOAD")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .after(Map.of("templateCode", template.getCode()))
                .build());
        return structureResponse(examId, doc, examRepository.findById(examId)
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
                new ExamStructureDocument(request.parts(), request.examQuotas()));
        ExamStructure stored = structureRepository.findById(examId).orElse(null);
        ExamStructureDocument storedDoc = stored == null ? null : readStoredStructure(stored);
        String beforeHash = stored == null ? null : stored.getStructureHash();
        ExamTemplate template = exam.getTemplateCode() == null ? null
                : templateService.findActive(exam.getTemplateCode());
        if (exam.getTemplateCode() != null && template == null) {
            throw new BusinessRuleException(ExamErrorCodes.TEMPLATE_MISMATCH,
                    messages.get(ExamErrorCodes.TEMPLATE_MISMATCH));
        }
        ExamStructureDocument templateDoc = template == null ? null
                : codec().read(template.getStructure());
        ExamStructureDocument doc = ExamStructureOps.restoreTemplateOwned(incoming, storedDoc,
                templateDoc);
        ExamStructureValidator.validate(doc, structureProperties.maxTopicQuotas());
        validateStructureReferences(doc);
        List<String> changedPartIds = changedPartIds(storedDoc, doc);
        writeStructure(exam, doc, true, actor, now);

        Map<String, Object> beforeStructure = new LinkedHashMap<>();
        beforeStructure.put("structureHash", beforeHash);
        beforeStructure.put("changedPartIds", changedPartIds);
        Map<String, Object> afterStructure = new LinkedHashMap<>();
        afterStructure.put("structureHash", structureRepository.findById(examId)
                .map(ExamStructure::getStructureHash).orElse(null));
        afterStructure.put("changedPartIds", changedPartIds);
        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM.STRUCTURE_UPDATE")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .before(beforeStructure)
                .after(afterStructure)
                .build());
        return structureResponse(examId, doc, examRepository.findById(examId)
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
        String kindCode = catalogLookup.findItem(source.getKindItemId())
                .map(CatalogItemView::code)
                .orElseThrow(() -> new BusinessRuleException(ExamErrorCodes.KIND_UNKNOWN));
        String code = requireCode(suggestExamCode(kindCode, source.getScope(), source.getPartCode(),
                subjectCodeOf(source.getSubjectItemId())).code());

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
                : ExamStructureOps.regenerateIds(readStoredStructure(sourceStructure));
        structureRepository.save(new ExamStructure(copy.getId(), codec().writeParts(doc),
                codec().writeQuotas(doc.examQuotas()), hash(doc), null, false, actor, now));
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
                .map(this::readStoredStructure)
                .orElse(new ExamStructureDocument(List.of()));
    }

    private ExamStructureDocument readStoredStructure(ExamStructure structure) {
        return new ExamStructureDocument(codec().read(structure.getParts()).parts(),
                codec().readQuotas(structure.getExamQuotas()));
    }

    private void writeStructure(Exam exam, ExamStructureDocument doc, boolean edited,
            UUID actor, Instant now) {
        JsonNode partsJson = codec().writeParts(doc);
        JsonNode quotasJson = codec().writeQuotas(doc.examQuotas());
        String hash = hash(doc);
        ExamStructure structure = structureRepository.findById(exam.getId()).orElse(null);
        if (structure == null) {
            structureRepository.save(new ExamStructure(exam.getId(), partsJson, quotasJson, hash, null, edited,
                    actor, now));
        } else {
            structure.replace(partsJson, quotasJson, hash, edited, actor, now);
            structureRepository.save(structure);
        }
        ExamStructureSummary summary = summarize(exam, doc);
        exam.applyStructureSummary(summary.questionCount(), summary.hasEssay(),
                summary.hasEnglishOption(), summary.combinationCount());
        exam.markStructureChanged(actor, now);
        rewriteBlockRefs(exam.getId(), doc);
        examRepository.flush();
    }

    private ExamStructureSummary summarize(Exam exam, ExamStructureDocument doc) {
        int count = 0;
        if (doc.parts().stream().anyMatch(part -> part.codeSource() != null)) {
            count = (int) combinationLookup.listCombinations(exam.getKindItemId()).stream()
                    .map(CatalogCombinationView::itemCode).distinct().count();
        }
        return blockRefProjector.summarize(doc.parts(), count);
    }

    private void rewriteBlockRefs(UUID examId, ExamStructureDocument doc) {
        blockRefRepository.deleteByExamIdAndVersionNo(examId, null);
        blockRefRepository.flush();
        List<vn.unicom.thi360.exam.domain.ExamBlockRef> rows =
                blockRefProjector.project(examId, doc.parts(), resolver());
        if (!rows.isEmpty()) {
            blockRefRepository.saveAll(rows);
        }
    }

    private CodeResolver resolver() {
        return new CodeResolver(
                code -> catalogLookup.findActiveItem(SUBJECT_GROUP, code).map(CatalogItemView::id).orElse(null),
                code -> catalogLookup.findActiveItem("COMPETENCY", code).map(CatalogItemView::id).orElse(null),
                code -> catalogLookup.findActiveItem("QUESTION_TAG", code).map(CatalogItemView::id).orElse(null));
    }

    private void validateStructureReferences(ExamStructureDocument doc) {
        for (ExamPart part : doc.parts()) {
            for (ExamSection section : part.sections()) {
                validateSectionReferences(section);
            }
            for (ExamSection section : part.subjectPool()) {
                validateSectionReferences(section);
            }
            for (ExamPartOption option : part.options()) {
                if (option.kind() == OptionKind.SUBJECT) {
                    requireActiveReference(SUBJECT_GROUP, option.code());
                }
                option.sections().forEach(this::validateSectionReferences);
                validateBlockReferences(option.blocks());
            }
        }
        for (var quota : doc.examQuotas()) {
            String group = quota.dimension() == vn.unicom.thi360.exam.domain.ExamQuota.Dimension.GRADE
                    ? "GRADE" : "QUESTION_LEVEL";
            quota.buckets().forEach(bucket -> bucket.codes().forEach(code -> requireActiveReference(group, code)));
            quota.subjectCodes().forEach(code -> requireActiveReference(SUBJECT_GROUP, code));
            quota.excludeSubjectCodes().forEach(code -> requireActiveReference(SUBJECT_GROUP, code));
        }
    }

    private void validateSectionReferences(ExamSection section) {
        requireActiveReference(SUBJECT_GROUP, section.subjectCode());
        validateScopeReferences(section.knowledgeScope());
        validateMatrixReferences(section.matrix());
        Set<String> groupIds = section.blocks().stream().map(QuestionBlock::id)
                .collect(java.util.stream.Collectors.toSet());
        validateTopicReferences(section.topicQuotas(), groupIds);
        validateBlockReferences(section.blocks());
    }

    private void validateBlockReferences(List<QuestionBlock> blocks) {
        Set<String> groupIds = blocks.stream().map(QuestionBlock::id)
                .collect(java.util.stream.Collectors.toSet());
        for (QuestionBlock block : blocks) {
            block.tagCodes().forEach(code -> requireActiveReference("QUESTION_TAG", code));
            validateScopeReferences(block.knowledgeScope());
            validateMatrixReferences(block.matrix());
            block.slots().forEach(slot -> {
                validateScopeReferences(slot.knowledgeScope());
                slot.tagCodes().forEach(code -> requireActiveReference("QUESTION_TAG", code));
            });
            block.childConditions().forEach(condition -> validateKnowledgeIds(condition.nodeIds()));
            validateTopicReferences(block.topicQuotas(), groupIds);
        }
    }

    private void validateTopicReferences(List<vn.unicom.thi360.exam.domain.TopicQuota> quotas, Set<String> groupIds) {
        for (var quota : quotas) {
            validateKnowledgeIds(quota.nodeIds());
            quota.gradeCodes().forEach(code -> requireActiveReference("GRADE", code));
            quota.difficultyCodes().forEach(code -> requireActiveReference("QUESTION_LEVEL", code));
            if (!groupIds.containsAll(quota.groupIds())) {
                throw new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
            }
        }
    }

    private void validateKnowledgeIds(List<UUID> nodeIds) {
        for (UUID nodeId : nodeIds) {
            if (catalogLookup.findItem(nodeId).filter(item -> "KNOWLEDGE".equals(item.groupCode())
                    && item.effectiveStatus() == CatalogStatus.ACTIVE).isEmpty()) {
                throw new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
            }
        }
    }
    private void validateScopeReferences(KnowledgeScope scope) {
        if (scope == null) {
            return;
        }
        validateKnowledgeIds(scope.nodeIds());
        for (String competency : scope.competencyCodes()) {
            requireActiveReference("COMPETENCY", competency);
        }
    }

    private void validateMatrixReferences(BlockMatrix matrix) {
        if (matrix == null) {
            return;
        }
        matrix.cells().forEach(cell -> {
            requireActiveReference("GRADE", cell.gradeCode());
            requireActiveReference("QUESTION_LEVEL", cell.difficultyCode());
        });
        matrix.byGrade().forEach(value -> requireActiveReference("GRADE", value.code()));
        matrix.byDifficulty().forEach(value -> requireActiveReference("QUESTION_LEVEL", value.code()));
        matrix.byCompetency().forEach(value -> requireActiveReference("COMPETENCY", value.code()));
    }

    private void requireActiveReference(String group, String code) {
        if (code == null || code.isBlank() || catalogLookup.findActiveItem(group, code).isEmpty()) {
            throw new BusinessRuleException(ExamErrorCodes.STRUCTURE_INVALID);
        }
    }

    private Map<String, Object> examInfoSnapshot(Exam exam) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", exam.getCode());
        values.put("name", exam.getName());
        values.put("description", exam.getDescription());
        values.put("instructions", exam.getInstructions());
        values.put("kindItemId", exam.getKindItemId());
        values.put("scope", exam.getScope());
        values.put("partCode", exam.getPartCode());
        values.put("subjectItemId", exam.getSubjectItemId());
        values.put("templateCode", exam.getTemplateCode());
        values.put("structureYear", exam.getStructureYear());
        values.put("matrixYear", exam.getMatrixYear());
        values.put("yearsConfirmed", exam.isYearsConfirmed());
        values.put("timing", exam.getTiming());
        values.put("durationMinutes", exam.getDurationMinutes());
        values.put("maxScore", exam.getMaxScore());
        values.put("referenceMax", exam.getReferenceMax());
        values.put("practiceScoring", exam.getPracticeScoring());
        return values;
    }

    private List<String> changedPartIds(ExamStructureDocument before, ExamStructureDocument after) {
        Map<String, ExamPart> previous = new LinkedHashMap<>();
        Map<String, Integer> previousPositions = new LinkedHashMap<>();
        if (before != null) {
            for (int i = 0; i < before.parts().size(); i++) {
                ExamPart part = before.parts().get(i);
                previous.put(part.id(), part);
                previousPositions.put(part.id(), i);
            }
        }
        Set<String> changed = new LinkedHashSet<>();
        for (int i = 0; i < after.parts().size(); i++) {
            ExamPart part = after.parts().get(i);
            if (!Objects.equals(previous.remove(part.id()), part)
                    || !Objects.equals(previousPositions.get(part.id()), i)) {
                changed.add(part.id());
            }
        }
        changed.addAll(previous.keySet());
        return List.copyOf(changed);
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

    /** Validates the scope and resolves its active part or subject. */
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
            if (!"SUBJECT".equals(profile.getTemplateBy())
                    && templateService.findBestMatch(kindItem.code(), "PART", part, null, null) == null) {
                throw new BusinessRuleException(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED,
                        messages.get(ExamErrorCodes.PART_OR_SUBJECT_REQUIRED));
            }
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


    private void assertTemplateMatches(Exam exam, ExamTemplate template) {
        String kindCode = catalogLookup.findItem(exam.getKindItemId())
                .map(CatalogItemView::code).orElse(null);
        String subjectCode = subjectCodeOf(exam.getSubjectItemId());
        if (!Objects.equals(kindCode, template.getKindCode())
                || (!Objects.equals(exam.getScope(), template.getScope())
                        && !("PART".equals(exam.getScope()) && "SUBJECT".equals(template.getScope())
                        && ("SP2E".equals(kindCode) || "SPT".equals(kindCode))))
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
        versions.add(new ExamVersionResponse(null, "WORKING", null, null, 0, null, List.of(), List.of()));
        versions.addAll(ExamVersionQueryService.publishedRows(jdbcTemplate, objectMapper, userDirectory, exam.getId()));
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
                "SHARED".equals(exam.getTiming()) ? exam.getDurationMinutes() : null,
                exam.getMaxScore(),
                exam.getReferenceMax(),
                exam.getPracticeScoring(),
                exam.getTemplateCode(),
                exam.getBuildMode(),
                exam.getStructureYear(),
                exam.getMatrixYear(),
                exam.isYearsConfirmed(),
                new ExamCheckSummary(checkValidity.statuses(List.of(exam.getId()))
                        .getOrDefault(exam.getId(), exam.getCheckStatus()),
                        exam.getCheckIssueCount(), exam.getCheckedAt()),
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
