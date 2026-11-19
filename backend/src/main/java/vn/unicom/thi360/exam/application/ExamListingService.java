package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamListingResponse;
import vn.unicom.thi360.exam.api.UpdateExamListingRequest;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamListing;
import vn.unicom.thi360.exam.infrastructure.ExamCoverAssetRepository;
import vn.unicom.thi360.exam.infrastructure.ExamListingRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/** Reads and saves the working sale listing of an exam (TM 6.3). */
@Service
public class ExamListingService {
    static final int SUMMARY_MAX = 300;
    static final int LONG_TEXT_MAX = 2_000;
    static final int BENEFIT_NOTE_MAX = 1_000;
    static final int DESCRIPTION_MAX = 4_000;
    private static final Set<String> RESULT_VIEWS = Set.of("SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION");

    private final ExamRepository exams;
    private final ExamListingRepository listings;
    private final ExamCoverAssetRepository covers;
    private final ExamListingChecks checks;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditRecorder audit;
    private final CurrentUserProvider currentUser;
    private final AppMessages messages;
    private final Clock clock;
    private final ExamCoverService coverService;
    private final EntityManager entityManager;

    public ExamListingService(ExamRepository exams, ExamListingRepository listings, ExamCoverAssetRepository covers,
            ExamListingChecks checks, JdbcTemplate jdbc, ObjectMapper mapper, AuditRecorder audit,
            CurrentUserProvider currentUser, AppMessages messages, Clock clock, ExamCoverService coverService,
            EntityManager entityManager) {
        this.exams = exams;
        this.listings = listings;
        this.covers = covers;
        this.checks = checks;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.currentUser = currentUser;
        this.messages = messages;
        this.clock = clock;
        this.coverService = coverService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public ExamListingResponse get(UUID examId) {
        Exam exam = exams.findById(examId).orElseThrow(() -> new NotFoundException("Exam not found"));
        return response(exam, listings.findById(examId).orElse(null));
    }

    @Transactional
    public ExamListingResponse update(UUID examId, UpdateExamListingRequest request) {
        Exam exam = exams.findByIdForUpdate(examId).orElseThrow(() -> new NotFoundException("Exam not found"));
        long current = exam.getRevision() == null ? 0L : exam.getRevision();
        if (request.expectedRevision() == null || request.expectedRevision() != current) {
            throw new VersionConflictException(ExamErrorCodes.REVISION_CONFLICT,
                    messages.get(ExamErrorCodes.REVISION_CONFLICT));
        }
        List<FieldError> errors = new ArrayList<>();
        String summary = text(request.summary(), "summary", SUMMARY_MAX, errors);
        String objectives = text(request.objectives(), "objectives", LONG_TEXT_MAX, errors);
        String audience = text(request.audience(), "audience", LONG_TEXT_MAX, errors);
        String benefitNote = text(request.benefitNote(), "benefitNote", BENEFIT_NOTE_MAX, errors);
        String description = text(request.description(), "description", DESCRIPTION_MAX, errors);
        if (request.listPriceVnd() != null && request.listPriceVnd() < 0) {
            errors.add(fieldError("listPriceVnd", "NEGATIVE", messages.get("EXAM_LISTING_FIELD_NEGATIVE")));
        }
        if (request.attemptsGranted() != null && (request.attemptsGranted() < 1 || request.attemptsGranted() > 100)) {
            errors.add(fieldError("attemptsGranted", "OUT_OF_RANGE", messages.get("EXAM_LISTING_FIELD_OUT_OF_RANGE", 1, 100)));
        }
        String resultView = request.resultView() == null ? ExamListing.ResultView.SCORE_AND_ANSWERS.name() : request.resultView();
        if (!RESULT_VIEWS.contains(resultView)) {
            errors.add(fieldError("resultView", "UNKNOWN_VALUE", messages.get("EXAM_LISTING_FIELD_UNKNOWN_VALUE")));
        }
        // The cover row is locked until this transaction ends: the cleanup sweep claims rows with SKIP LOCKED and its
        // claim marks the row, so either this save sees the claim (not found) or the sweep skips the row.
        if (request.coverAssetId() != null && covers.lockAttachable(request.coverAssetId(), examId).isEmpty()) {
            errors.add(fieldError("coverAssetId", "COVER_NOT_FOUND", messages.get("EXAM_LISTING_FIELD_COVER_NOT_FOUND")));
        }
        if (!errors.isEmpty()) {
            throw new BusinessRuleException(ExamErrorCodes.LISTING_INVALID, messages.get(ExamErrorCodes.LISTING_INVALID), errors);
        }
        Instant now = Instant.now(clock);
        UUID actor = currentUser.requireCurrentUser().id();
        ExamListing listing = listings.findById(examId).orElseGet(() -> new ExamListing(examId, now));
        Map<String, Object> before = auditView(exam, listing.fields());
        listing.replace(new ExamListing.Fields(summary, objectives, audience, request.listPriceVnd(),
                request.attemptsGranted(), Boolean.TRUE.equals(request.benefitAiExplanation()),
                Boolean.TRUE.equals(request.benefitStrengthWeakness()), Boolean.TRUE.equals(request.benefitPractice()),
                benefitNote, ExamListing.ResultView.valueOf(resultView)), actor, now);
        listings.save(listing);
        exam.changeListing(description, request.coverAssetId(), actor, now);
        exams.saveAndFlush(exam);
        // The revision is what two editors race on, so every save moves it: not only when the exam row happens to differ
        // (same editor, same clock instant, only listing columns changed leaves the row clean) (R1-5).
        if (exam.getRevision() == null || exam.getRevision() == current) {
            entityManager.lock(exam, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        if (request.coverAssetId() != null) covers.clearOrphaned(request.coverAssetId());
        audit.record(AuditEvent.builder("EXAM", "EXAM_LISTING_UPDATED")
                .target("EXAM", exam.getId().toString(), exam.getCode())
                .before(before)
                .after(auditView(exam, listing.fields()))
                .build());
        return response(exam, listing);
    }

    /**
     * Copies the object of the source exam's cover for a duplicate. Call it outside a transaction (the object store is
     * slow and must not run under the command's database locks); null when the source has no cover. A cover whose
     * row or object is gone fails the command instead of dropping the cover.
     */
    public ExamCoverService.StagedCover stageCoverCopy(UUID sourceExamId) {
        UUID cover = exams.findById(sourceExamId).map(Exam::getCoverAssetId).orElse(null);
        return cover == null ? null : coverService.stageCopy(cover);
    }

    /** Removes the object of a staged copy that was not used. */
    public void discardStagedCover(ExamCoverService.StagedCover staged) {
        coverService.discard(staged);
    }

    /**
     * Gives a duplicated exam the sale listing and cover of its source (TM 6.2). The cover is the copy staged before the
     * transaction as a new image, so each exam's cover cleanup acts on its own object. The copy has no published
     * version, hence no {@code published}. If the source's cover changed between staging and now, the command fails
     * with a revision conflict (retry copies the current cover) instead of copying a cover the source no longer has.
     */
    @Transactional
    public void copyInto(Exam source, Exam copy, UUID actor, Instant now, ExamCoverService.StagedCover staged) {
        listings.findById(source.getId()).ifPresent(original -> {
            ExamListing duplicate = new ExamListing(copy.getId(), now);
            duplicate.copyFrom(original, actor, now);
            listings.save(duplicate);
        });
        if (source.getCoverAssetId() == null) return;
        if (staged == null || !staged.sourceAssetId().equals(source.getCoverAssetId())) {
            throw new VersionConflictException(ExamErrorCodes.REVISION_CONFLICT, messages.get(ExamErrorCodes.REVISION_CONFLICT));
        }
        UUID asset = coverService.attach(staged, copy.getId());
        copy.changeListing(copy.getDescription(), asset, actor, now);
        exams.saveAndFlush(copy);
    }

    private ExamListingResponse response(Exam exam, ExamListing listing) {
        ExamListing.Fields fields = listing == null ? null : listing.fields();
        UUID cover = exam.getCoverAssetId();
        var issues = checks.issues(exam.getId());
        return new ExamListingResponse(exam.getId(), exam.getRevision() == null ? 0L : exam.getRevision(),
                exam.isHasUnpublishedChanges(),
                fields == null ? null : fields.summary(), fields == null ? null : fields.objectives(),
                fields == null ? null : fields.audience(), fields == null ? null : fields.listPriceVnd(),
                fields == null ? null : fields.attemptsGranted(),
                fields != null && fields.benefitAiExplanation(), fields != null && fields.benefitStrengthWeakness(),
                fields != null && fields.benefitPractice(), fields == null ? null : fields.benefitNote(),
                fields == null ? ExamListing.ResultView.SCORE_AND_ANSWERS.name() : fields.resultView().name(),
                exam.getDescription(), cover, cover == null ? null : ExamCoverService.urlOf(exam.getId(), cover),
                listing == null ? null : listing.getUpdatedAt(), published(exam.getId()), issues, checks.minPriceVnd());
    }

    /** The listing of the latest version that still serves a path (E24); empty for a version older than EX-1. */
    private ExamListingResponse.Published published(UUID examId) {
        var rows = jdbc.query("SELECT version_no, published_at, config->'listing' FROM exam.exam_version "
                        + "WHERE exam_id = ? AND state = 'SERVING' ORDER BY version_no DESC LIMIT 1",
                (rs, n) -> new Object[] {rs.getInt(1), rs.getTimestamp(2).toInstant(), rs.getString(3)}, examId);
        if (rows.isEmpty() || rows.getFirst()[2] == null) return null;
        try {
            JsonNode listing = mapper.readTree((String) rows.getFirst()[2]);
            return new ExamListingResponse.Published((Integer) rows.getFirst()[0], (Instant) rows.getFirst()[1],
                    ExamListingSnapshot.sale(examId, listing));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid exam version snapshot", e);
        }
    }

    private String text(String value, String field, int max, List<FieldError> errors) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > max) {
            errors.add(fieldError(field, "TOO_LONG", messages.get("EXAM_LISTING_FIELD_TOO_LONG", max)));
        }
        return trimmed;
    }

    private static FieldError fieldError(String field, String code, String message) {
        return new FieldError(field, code, message);
    }

    private static Map<String, Object> auditView(Exam exam, ExamListing.Fields fields) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("summary", fields.summary());
        view.put("objectives", fields.objectives());
        view.put("audience", fields.audience());
        view.put("listPriceVnd", fields.listPriceVnd());
        view.put("attemptsGranted", fields.attemptsGranted());
        view.put("benefitAiExplanation", fields.benefitAiExplanation());
        view.put("benefitStrengthWeakness", fields.benefitStrengthWeakness());
        view.put("benefitPractice", fields.benefitPractice());
        view.put("benefitNote", fields.benefitNote());
        view.put("resultView", fields.resultView().name());
        view.put("description", exam.getDescription());
        view.put("coverAssetId", exam.getCoverAssetId() == null ? null : exam.getCoverAssetId().toString());
        return view;
    }
}
