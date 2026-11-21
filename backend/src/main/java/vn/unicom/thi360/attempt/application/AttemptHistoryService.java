package vn.unicom.thi360.attempt.application;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.api.AttemptHistoryItem;
import vn.unicom.thi360.attempt.api.AttemptHistoryItem.HistoryAction;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;

/**
 * Queries the learner's practice history with filters, deterministic sorting, and N+1-free enrichment (LT §11).
 */
@Service
public class AttemptHistoryService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AttemptAccessGate accessGate;
    private final AttemptRepository attemptRepository;
    private final AttemptEventRepository attemptEventRepository;
    private final CatalogLookup catalogLookup;
    private final MessageSource messageSource;

    public AttemptHistoryService(
            AttemptAccessGate accessGate,
            AttemptRepository attemptRepository,
            AttemptEventRepository attemptEventRepository,
            CatalogLookup catalogLookup,
            MessageSource messageSource) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptRepository = Objects.requireNonNull(attemptRepository);
        this.attemptEventRepository = Objects.requireNonNull(attemptEventRepository);
        this.catalogLookup = Objects.requireNonNull(catalogLookup);
        this.messageSource = Objects.requireNonNull(messageSource);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptHistoryItem> listHistory(
            String q,
            String kind,
            String scope,
            UUID examId,
            String from,
            String to,
            Integer page,
            Integer size) {
        UUID userId = accessGate.requireLearner();
        Instant fromInstant = parseBound(from, true);
        Instant toInstant = parseBound(to, false);

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        Specification<Attempt> spec = buildSpecification(userId, q, kind, scope, examId, fromInstant, toInstant);
        Page<Attempt> attemptPage = attemptRepository.findAll(spec, pageable);

        List<Attempt> rows = attemptPage.getContent();
        if (rows.isEmpty()) {
            return PageResponse.from(attemptPage.map(a -> toHistoryItem(a, Map.of(), Set.of(), null)));
        }

        List<UUID> attemptIds = rows.stream().map(Attempt::getId).toList();
        Set<UUID> compensatedIds = new HashSet<>(
                attemptEventRepository.findAttemptIdsWithType(attemptIds, AttemptEventType.COMPENSATED));

        Map<UUID, String> subjectCodesById = new HashMap<>();
        boolean needsSubjectLookup = rows.stream().anyMatch(a -> a.getSubjectItemId() != null);
        if (needsSubjectLookup) {
            for (CatalogItemView item : catalogLookup.listGroupItems("SUBJECT")) {
                subjectCodesById.put(item.id(), item.code());
            }
        }

        String regradingLabel = messageSource.getMessage(
                "attempt.result.regrading.label",
                null,
                "Đang chấm lại",
                LocaleContextHolder.getLocale());

        return PageResponse.from(attemptPage, a -> toHistoryItem(a, subjectCodesById, compensatedIds, regradingLabel));
    }

    private static Specification<Attempt> buildSpecification(
            UUID userId,
            String q,
            String kind,
            String scope,
            UUID examId,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.not(root.get("state").in(List.of(AttemptState.STARTING, AttemptState.ABORTED))));

            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("examName")), pattern, '\\'));
            }
            if (kind != null && !kind.isBlank()) {
                predicates.add(cb.equal(root.get("examKindCode"), kind.trim()));
            }
            if (scope != null && !scope.isBlank()) {
                predicates.add(cb.equal(root.get("scope"), scope.trim()));
            }
            if (examId != null) {
                predicates.add(cb.equal(root.get("examId"), examId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), to));
            }
            if (query != null
                    && query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                query.orderBy(
                        cb.desc(cb.coalesce(root.get("startedAt"), cb.literal(Instant.EPOCH))),
                        cb.desc(root.get("createdAt")),
                        cb.desc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AttemptHistoryItem toHistoryItem(
            Attempt attempt,
            Map<UUID, String> subjectCodesById,
            Set<UUID> compensatedIds,
            String regradingLabel) {
        boolean showScores = attempt.getState() == AttemptState.SCORED
                || (attempt.getState() == AttemptState.PENDING_REVIEW && attempt.isRegrading());

        BigDecimal rawScore = showScores ? attempt.getRawScore() : null;
        BigDecimal displayScore = showScores ? attempt.getDisplayScore() : null;
        BigDecimal maxScore = showScores ? attempt.getMaxScore() : null;
        BigDecimal scaleMax = showScores ? resolveScaleMax(attempt) : null;

        HistoryAction action = attempt.getState() == AttemptState.IN_PROGRESS
                ? HistoryAction.CONTINUE
                : HistoryAction.VIEW_RESULT;

        String subjectCode = attempt.getSubjectItemId() != null
                ? subjectCodesById.get(attempt.getSubjectItemId())
                : null;

        return new AttemptHistoryItem(
                attempt.getId(),
                attempt.getExamId(),
                attempt.getExamVersionNo(),
                attempt.getExamName(),
                attempt.getExamKindCode(),
                attempt.getScope(),
                attempt.getPartCode(),
                subjectCode,
                attempt.getChoices(),
                attempt.getAttemptNo(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getGradedAt(),
                attempt.getState(),
                attempt.isRegrading(),
                attempt.isRegrading() ? regradingLabel : null,
                attempt.getPracticeScoring(),
                rawScore,
                displayScore,
                maxScore,
                scaleMax,
                action,
                attempt.getResultView(),
                attempt.getFaultFlag(),
                attempt.getFaultReason(),
                compensatedIds.contains(attempt.getId()));
    }

    private static BigDecimal resolveScaleMax(Attempt attempt) {
        if (attempt.getResult() != null && attempt.getResult().get("scaleMax") != null) {
            Object raw = attempt.getResult().get("scaleMax");
            if (raw instanceof BigDecimal bd) {
                return bd;
            }
            if (raw instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            try {
                return new BigDecimal(raw.toString());
            } catch (NumberFormatException ignored) {
                // Fall back to referenceMax / maxScore
            }
        }
        if ("LINEAR".equals(attempt.getPracticeScoring())
                && attempt.getReferenceMax() != null
                && attempt.getReferenceMax().compareTo(BigDecimal.ZERO) > 0) {
            return attempt.getReferenceMax();
        }
        return attempt.getMaxScore();
    }

    private static Instant parseBound(String raw, boolean startOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(trimmed);
                if (startOfDay) {
                    return date.atStartOfDay(DISPLAY_ZONE).toInstant();
                }
                return date.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant().minusNanos(1);
            } catch (DateTimeParseException ex) {
                throw new ValidationException("INVALID_DATE_FILTER", "Invalid date parameter: " + trimmed, List.of());
            }
        }
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
