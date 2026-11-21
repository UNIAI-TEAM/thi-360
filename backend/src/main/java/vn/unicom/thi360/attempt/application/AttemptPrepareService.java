package vn.unicom.thi360.attempt.application;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.ExamKindView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.GrantSourceView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.ListeningPolicyView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.OmittedGroupView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.OpenAttemptView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.SubjectView;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.BlockSummary;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
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
        AllowedOptions allowedOptions =
                resolveAllowedOptions(serving.servingPaths(), desc.optionCodes());
        List<String> answerFormats = deriveAnswerFormats(version, allowedOptions);
        ListeningPolicyView listeningPolicy = deriveListeningPolicy(version, allowedOptions);
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
                structure.afterStartChoices(),
                answerFormats,
                listeningPolicy);
    }

    private record AllowedOptions(
            boolean filter,
            Map<String, Set<String>> optionIdsByPart,
            Map<String, Set<String>> optionCodesByPart,
            Set<String> globalOptionCodes) {

        boolean isOptionAllowed(String partId, Option option) {
            if (!filter) {
                return true;
            }
            if (option.id() != null
                    && optionIdsByPart.getOrDefault(partId, Set.of()).contains(option.id())) {
                return true;
            }
            String normCode = normalizeCode(option.code());
            if (normCode == null) {
                return false;
            }
            return optionCodesByPart.getOrDefault(partId, Set.of()).contains(normCode)
                    || (optionIdsByPart.isEmpty()
                            && optionCodesByPart.isEmpty()
                            && globalOptionCodes.contains(normCode));
        }

        Set<String> allowedOptionIdsForPart(Part part) {
            if (!filter || part.options() == null || part.options().isEmpty()) {
                return Set.of();
            }
            Set<String> allowedIds = new LinkedHashSet<>();
            for (Option option : part.options()) {
                if (option.id() != null && isOptionAllowed(part.id(), option)) {
                    allowedIds.add(option.id());
                }
            }
            return allowedIds;
        }
    }

    private static AllowedOptions resolveAllowedOptions(
            List<ServingPath> servingPaths, List<String> grantedOptionCodes) {
        Set<String> granted = new LinkedHashSet<>();
        if (grantedOptionCodes != null) {
            for (String code : grantedOptionCodes) {
                String normalized = normalizeCode(code);
                if (normalized != null) {
                    granted.add(normalized);
                }
            }
        }
        boolean hasServingPaths = servingPaths != null && !servingPaths.isEmpty();
        if (!hasServingPaths) {
            if (granted.isEmpty()) {
                return new AllowedOptions(false, Map.of(), Map.of(), Set.of());
            }
            return new AllowedOptions(true, Map.of(), Map.of(), Set.copyOf(granted));
        }

        Map<String, Set<String>> optionIdsByPart = new LinkedHashMap<>();
        Map<String, Set<String>> optionCodesByPart = new LinkedHashMap<>();
        for (ServingPath path : servingPaths) {
            if (!granted.isEmpty() && path.choices() != null && !path.choices().isEmpty()) {
                boolean allGranted =
                        path.choices().stream()
                                .map(PathChoice::code)
                                .map(AttemptPrepareService::normalizeCode)
                                .allMatch(c -> c != null && granted.contains(c));
                if (!allGranted) {
                    continue;
                }
            }
            if (path.choices() == null) {
                continue;
            }
            for (PathChoice choice : path.choices()) {
                if (choice.partId() == null) {
                    continue;
                }
                if (choice.optionId() != null) {
                    optionIdsByPart
                            .computeIfAbsent(choice.partId(), k -> new LinkedHashSet<>())
                            .add(choice.optionId());
                }
                String normCode = normalizeCode(choice.code());
                if (normCode != null) {
                    optionCodesByPart
                            .computeIfAbsent(choice.partId(), k -> new LinkedHashSet<>())
                            .add(normCode);
                }
            }
        }
        return new AllowedOptions(true, optionIdsByPart, optionCodesByPart, Set.copyOf(granted));
    }

    private static List<String> deriveAnswerFormats(
            PublishedVersion version, AllowedOptions allowedOptions) {
        if (version.parts() == null) {
            return List.of();
        }
        Set<String> formats = new LinkedHashSet<>();
        for (Part part : version.parts()) {
            if (part.blocks() == null) {
                continue;
            }
            Set<String> allowedPartOptionIds = allowedOptions.allowedOptionIdsForPart(part);
            for (BlockSummary block : part.blocks()) {
                if (block.omitted()) {
                    continue;
                }
                if (allowedOptions.filter()
                        && block.optionId() != null
                        && !allowedPartOptionIds.contains(block.optionId())) {
                    continue;
                }
                String format =
                        block.childAnswerType() != null && !block.childAnswerType().isBlank()
                                ? block.childAnswerType()
                                : block.questionType();
                if (format != null && !format.isBlank()) {
                    formats.add(format);
                }
            }
        }
        return List.copyOf(formats);
    }

    private static ListeningPolicyView deriveListeningPolicy(
            PublishedVersion version, AllowedOptions allowedOptions) {
        if (version.parts() == null) {
            return null;
        }
        for (Part part : version.parts()) {
            if (hasRequiredAudio(part.sections()) || hasRequiredAudio(part.subjectPool())) {
                return new ListeningPolicyView(AttemptAudioPlay.MAX_PLAYS, false);
            }
            if (part.options() != null) {
                for (Option option : part.options()) {
                    if (allowedOptions.isOptionAllowed(part.id(), option)
                            && hasRequiredAudio(option.sections())) {
                        return new ListeningPolicyView(AttemptAudioPlay.MAX_PLAYS, false);
                    }
                }
            }
        }
        return null;
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasRequiredAudio(List<Section> sections) {
        if (sections == null) {
            return false;
        }
        for (Section section : sections) {
            if ("REQUIRED".equalsIgnoreCase(section.audioRequirement())) {
                return true;
            }
        }
        return false;
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
