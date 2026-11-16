package vn.unicom.thi360.exam.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamKindOption;
import vn.unicom.thi360.exam.api.UpdateExamKindProfileRequest;
import vn.unicom.thi360.exam.domain.ExamKindProfile;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;
import vn.unicom.thi360.shared.web.LocaleConfig;

/** Hồ sơ kỳ thi (exam_kind_profile): đọc theo danh mục EXAM, PUT thay thế toàn bộ (spec 6). */
@Service
public class ExamKindService {

    static final String EXAM_GROUP = "EXAM";
    private static final Set<String> SCOPES = Set.of("FULL", "PART", "SUBJECT");
    private static final Set<String> TEMPLATE_BY = Set.of("KIND", "SUBJECT");
    private static final Set<String> TIMINGS = Set.of("PER_PART", "SHARED");
    private static final Set<String> PRACTICE_SCORING = Set.of("RAW", "LINEAR");
    private static final Set<String> MC_RULES = Set.of("ALL_OR_NOTHING", "PARTIAL");

    private final ExamKindProfileRepository profileRepository;
    private final CatalogLookup catalogLookup;
    private final ContentReferenceService contentReferenceService;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;
    private final AppMessages messages;
    private final JdbcTemplate jdbc;

    public ExamKindService(
            ExamKindProfileRepository profileRepository,
            CatalogLookup catalogLookup,
            ContentReferenceService contentReferenceService,
            AuditRecorder auditRecorder,
            CurrentUserProvider currentUserProvider,
            AppMessages messages, JdbcTemplate jdbc) {
        this.profileRepository = profileRepository;
        this.catalogLookup = catalogLookup;
        this.contentReferenceService = contentReferenceService;
        this.auditRecorder = auditRecorder;
        this.currentUserProvider = currentUserProvider;
        this.messages = messages;
        this.jdbc = jdbc;
    }

    /** Danh sách kỳ thi (giá trị nhóm EXAM đang hoạt động) kèm hồ sơ; chưa có hồ sơ → profileSet = false. */
    @Transactional(readOnly = true)
    public List<ExamKindOption> listExamKinds() {
        Map<UUID, ExamKindProfile> profiles = profileRepository.findAll().stream()
                .collect(Collectors.toMap(ExamKindProfile::getExamItemId, p -> p, (a, b) -> a,
                        LinkedHashMap::new));
        boolean english = isEnglish();
        List<ExamKindOption> out = new ArrayList<>();
        for (CatalogItemView item : catalogLookup.listActiveGroupItems(EXAM_GROUP)) {
            ExamKindProfile p = profiles.get(item.id());
            out.add(toOption(item, p, english));
        }
        return out;
    }

    /** Hồ sơ của một kỳ thi theo id giá trị danh mục; null khi chưa có hồ sơ. */
    @Transactional(readOnly = true)
    public ExamKindProfile findProfile(UUID examItemId) {
        return profileRepository.findById(examItemId).orElse(null);
    }

    private ExamKindOption toOption(CatalogItemView item, ExamKindProfile p, boolean english) {
        String name = english && item.nameEn() != null && !item.nameEn().isBlank()
                ? item.nameEn() : item.nameVi();
        if (p == null) {
            return new ExamKindOption(item.id(), item.code(), name, List.of(), null, null,
                    null, null, null, null, null, false);
        }
        return new ExamKindOption(item.id(), item.code(), name, p.getScopes(), p.getTemplateBy(),
                p.getTiming(), p.getMaxScore(), p.getReferenceMax(), p.getPracticeScoring(),
                p.getTfScoring(), p.getMultipleChoiceRule(), true);
    }

    /**
     * PUT /exam-kinds/{itemId}: thay thế toàn bộ hồ sơ của một giá trị EXAM đang hoạt động.
     * {@code expectedRevision} so với revision hồ sơ hiện tại (0 khi chưa có); lệch → 412.
     * Sau khi ghi, xóa cache content-reference vì examKinds thay đổi (contract D25).
     */
    @Transactional
    public ExamKindOption updateProfile(UUID itemId, UpdateExamKindProfileRequest request) {
        CatalogItemView item = catalogLookup.findItem(itemId)
                .filter(v -> EXAM_GROUP.equals(v.groupCode()))
                .orElseThrow(() -> new NotFoundException(
                        messages.get("error.notFound", "EXAM", itemId)));

        List<FieldError> errors = validateProfile(request);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Instant now = Instant.now();
        UUID actor = currentUserProvider.getCurrentUser().map(u -> u.id()).orElse(null);
        ExamKindProfile profile = profileRepository.findByExamItemIdForUpdate(itemId).orElse(null);
        long currentRevision = profile == null ? 0L : profile.getRevision();
        if (request.expectedRevision() == null || request.expectedRevision() != currentRevision) {
            throw new VersionConflictException(
                    ExamErrorCodes.REVISION_CONFLICT, messages.get(ExamErrorCodes.REVISION_CONFLICT));
        }

        Map<String, Object> before = profile == null ? null : snapshot(profile);
        if (profile == null) {
            profile = new ExamKindProfile(itemId, request.scopes(), request.templateBy(),
                    request.timing(), request.maxScore(), request.referenceMax(),
                    request.practiceScoring(), request.trueFalseScoring(),
                    request.multipleChoiceRule(), now, actor);
        } else {
            profile.replace(request.scopes(), request.templateBy(), request.timing(),
                    request.maxScore(), request.referenceMax(), request.practiceScoring(),
                    request.trueFalseScoring(), request.multipleChoiceRule(), now, actor);
        }
        profile = profileRepository.saveAndFlush(profile);
        jdbc.queryForList("SELECT id FROM exam.exam WHERE kind_item_id = ? ORDER BY id FOR UPDATE", UUID.class, itemId);
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true, "
                + "check_status = CASE WHEN check_status = 'UNCHECKED' THEN 'UNCHECKED' ELSE 'NEEDS_RECHECK' END, "
                + "revision = revision + 1, updated_at = ?, updated_by = ? WHERE kind_item_id = ?",
                java.sql.Timestamp.from(now), actor, itemId);
        auditRecorder.record(AuditEvent.builder("EXAM", "EXAM_KIND_PROFILE.UPDATE")
                .target("EXAM_KIND", itemId.toString(), item.code())
                .before(before)
                .after(snapshot(profile))
                .build());
        contentReferenceService.evictCache();
        return toOption(item, profile, isEnglish());
    }

    private Map<String, Object> snapshot(ExamKindProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scopes", p.getScopes());
        m.put("templateBy", p.getTemplateBy());
        m.put("timing", p.getTiming());
        m.put("maxScore", p.getMaxScore());
        m.put("referenceMax", p.getReferenceMax());
        m.put("practiceScoring", p.getPracticeScoring());
        m.put("tfScoring", p.getTfScoring());
        m.put("multipleChoiceRule", p.getMultipleChoiceRule());
        m.put("revision", p.getRevision());
        return m;
    }

    private List<FieldError> validateProfile(UpdateExamKindProfileRequest r) {
        List<FieldError> errors = new ArrayList<>();
        if (r.scopes() == null || r.scopes().isEmpty()) {
            errors.add(field("scopes", messages.get(ExamErrorCodes.SCOPE_NOT_ALLOWED)));
        } else {
            for (String s : r.scopes()) {
                if (s == null || !SCOPES.contains(s)) {
                    errors.add(field("scopes", messages.get(ExamErrorCodes.SCOPE_NOT_ALLOWED)));
                    break;
                }
            }
        }
        if (r.templateBy() == null || !TEMPLATE_BY.contains(r.templateBy())) {
            errors.add(field("templateBy", messages.get("validation.exam.templateBy.invalid")));
        }
        if (r.timing() == null || !TIMINGS.contains(r.timing())) {
            errors.add(field("timing", messages.get("validation.exam.timing.invalid")));
        }
        if (r.practiceScoring() == null
                || !PRACTICE_SCORING.contains(r.practiceScoring())) {
            errors.add(field("practiceScoring", messages.get("validation.exam.practiceScoring.invalid")));
        }
        if (r.multipleChoiceRule() != null
                && !MC_RULES.contains(r.multipleChoiceRule())) {
            errors.add(field("multipleChoiceRule", messages.get("validation.exam.multipleChoiceRule.invalid")));
        }
        if (r.maxScore() != null && r.maxScore().signum() <= 0) {
            errors.add(field("maxScore", messages.get(ExamErrorCodes.MAX_SCORE_INVALID)));
        }
        if (r.referenceMax() != null && r.referenceMax().signum() <= 0) {
            errors.add(field("referenceMax", messages.get(ExamErrorCodes.MAX_SCORE_INVALID)));
        }
        return errors;
    }

    private static FieldError field(String field, String message) {
        return new FieldError(field, "EXAM_KIND_PROFILE_INVALID", message);
    }

    private static boolean isEnglish() {
        return java.util.Locale.ENGLISH.getLanguage()
                .equalsIgnoreCase(LocaleConfig.currentLocale().getLanguage());
    }
}
