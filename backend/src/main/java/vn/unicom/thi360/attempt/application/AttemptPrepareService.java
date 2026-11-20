package vn.unicom.thi360.attempt.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.ExamKindView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.GrantSourceView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.OmittedGroupView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.OpenAttemptView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.SubjectView;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.shared.api.NotFoundException;

/** Prepares the exam launch summary for an active learner's entitlement (LT §5.1, AC-11, AC-12). */
@Service
public class AttemptPrepareService {

    private static final List<AttemptState> OPEN_STATES =
            List.of(AttemptState.STARTING, AttemptState.IN_PROGRESS);

    private final AttemptAccessGate accessGate;
    private final EntitlementPort entitlementPort;
    private final PublishedExamPort publishedExamPort;
    private final AttemptRepository attemptRepository;
    private final OverdueAttemptCloser overdueAttemptCloser;
    private final CatalogLookup catalogLookup;
    private final AttemptPathResolver pathResolver;
    private final EntityManager entityManager;
    private final Clock clock;

    public AttemptPrepareService(
            AttemptAccessGate accessGate,
            EntitlementPort entitlementPort,
            PublishedExamPort publishedExamPort,
            AttemptRepository attemptRepository,
            OverdueAttemptCloser overdueAttemptCloser,
            CatalogLookup catalogLookup,
            AttemptPathResolver pathResolver,
            EntityManager entityManager,
            Clock clock) {
        this.accessGate = accessGate;
        this.entitlementPort = entitlementPort;
        this.publishedExamPort = publishedExamPort;
        this.attemptRepository = attemptRepository;
        this.overdueAttemptCloser = overdueAttemptCloser;
        this.catalogLookup = catalogLookup;
        this.pathResolver = pathResolver;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public AttemptPrepareResponse prepare(UUID entitlementId) {
        UUID userId = accessGate.requireLearner();
        EntitlementDescription desc = entitlementPort.describe(userId, entitlementId);
        Optional<Attempt> open = attemptRepository.findByUserIdAndExamIdAndStateIn(
                userId, desc.examId(), OPEN_STATES);
        if (open.isPresent() && open.get().getState() == AttemptState.IN_PROGRESS) {
            UUID openId = open.get().getId();
            entityManager.detach(open.get());
            overdueAttemptCloser.closeIfOverdue(openId, clock.instant());
            open = attemptRepository.findById(openId)
                    .filter(a -> OPEN_STATES.contains(a.getState()));
            if (open.isEmpty()) {
                desc = entitlementPort.describe(userId, entitlementId);
            }
        }

        ServingExam serving = publishedExamPort
                .findServing(desc.examId())
                .orElseThrow(() -> new NotFoundException("EXAM_NOT_FOUND", null));

        AttemptPathResolver.PrepareStructure structure =
                pathResolver.buildPrepareStructure(serving, desc.optionCodes(), desc.grantId() != null);
        PublishedVersion version = structure.representativeVersion();

        OpenAttemptView openView =
                open.map(a -> new OpenAttemptView(a.getId(), a.getState())).orElse(null);

        ExamKindView examKind = resolveExamKind(version);
        SubjectView subject = resolveSubject(version.subjectItemId());
        GrantSourceView grantSource = desc.grantId() == null
                ? null
                : new GrantSourceView(desc.source(), desc.orderCode(), desc.grantedAt());
        List<OmittedGroupView> omittedGroups =
                version.omittedGroups().stream()
                        .map(g -> new OmittedGroupView(g.name(), g.points()))
                        .toList();

        AttemptTiming timing = AttemptTiming.valueOf(version.timing());
        return new AttemptPrepareResponse(
                entitlementId,
                serving.examId(),
                version.code(),
                version.name(),
                examKind,
                version.scope(),
                version.partCode(),
                subject,
                version.buildMode(),
                grantSource,
                version.instructions(),
                timing,
                structure.durationMinutes(),
                NavigationMode.SEQUENTIAL,
                desc.attemptsLeft(),
                serving.status(),
                openView,
                omittedGroups,
                structure.parts(),
                structure.preChoice(),
                structure.afterStartChoices());
    }

    private ExamKindView resolveExamKind(PublishedVersion version) {
        Optional<CatalogItemView> item =
                version.kindItemId() == null ? Optional.empty() : catalogLookup.findItem(version.kindItemId());
        return new ExamKindView(
                version.kindItemId(),
                version.kindCode(),
                item.map(CatalogItemView::nameVi).orElse(null),
                item.map(CatalogItemView::nameEn).orElse(null));
    }

    private SubjectView resolveSubject(UUID subjectItemId) {
        if (subjectItemId == null) {
            return null;
        }
        return catalogLookup
                .findItem(subjectItemId)
                .map(i -> new SubjectView(i.id(), i.code(), i.nameVi(), i.nameEn()))
                .orElseGet(() -> new SubjectView(subjectItemId, null, null, null));
    }
}
