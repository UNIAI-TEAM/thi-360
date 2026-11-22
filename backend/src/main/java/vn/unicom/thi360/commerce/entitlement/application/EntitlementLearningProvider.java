package vn.unicom.thi360.commerce.entitlement.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.ProductRef;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementSource;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.identity.spi.StudentLearningProvider;

/**
 * The {@code entitlements} part of the learning profile (plan B1: one provider bean per module; the attempt
 * module supplies {@code attempts}). Batch-resolves exam code and name from {@code commerce.storefront_product}
 * with fallback to {@link PublishedExamPort#findServing(UUID)} for exams absent from the storefront projection.
 * An account without any entitlement returns {@link StudentLearningData#empty()}.
 */
@Component
public class EntitlementLearningProvider implements StudentLearningProvider {

    private static final Logger log = LoggerFactory.getLogger(EntitlementLearningProvider.class);

    private record ExamSummary(String code, String name) {
    }

    private final EntitlementRepository entitlementRepository;
    private final ExamAccessReadRepository examAccessReadRepository;
    private final PublishedExamPort publishedExamPort;
    private final ObjectMapper objectMapper;

    public EntitlementLearningProvider(
            EntitlementRepository entitlementRepository,
            ExamAccessReadRepository examAccessReadRepository,
            PublishedExamPort publishedExamPort,
            ObjectMapper objectMapper) {
        this.entitlementRepository = Objects.requireNonNull(entitlementRepository);
        this.examAccessReadRepository = Objects.requireNonNull(examAccessReadRepository);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningData loadForUser(UUID userId) {
        if (userId == null) {
            return StudentLearningData.empty();
        }
        List<Entitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        if (entitlements.isEmpty()) {
            return StudentLearningData.empty();
        }

        Map<UUID, ExamSummary> examsById = resolveExamSummaries(entitlements);
        List<EntitlementItem> items = entitlements.stream()
                .map(e -> {
                    ExamSummary summary = examsById.get(e.getExamId());
                    return new EntitlementItem(
                            e.getId(),
                            e.getExamId(),
                            summary == null ? null : summary.code(),
                            summary == null ? null : summary.name(),
                            mapSource(e.getSource()),
                            e.getAttemptsTotal(),
                            e.getAttemptsUsed(),
                            e.getAttemptsReserved(),
                            e.getAttemptsRevoked(),
                            e.attemptsLeft(),
                            computeStatus(e));
                })
                .toList();
        return new StudentLearningData(true, items, List.of(), List.of(), List.of(), List.of());
    }

    private Map<UUID, ExamSummary> resolveExamSummaries(List<Entitlement> entitlements) {
        Set<UUID> distinctExamIds = new LinkedHashSet<>(entitlements.size());
        for (Entitlement entitlement : entitlements) {
            if (entitlement.getExamId() != null) {
                distinctExamIds.add(entitlement.getExamId());
            }
        }
        if (distinctExamIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ExamSummary> resolved = new HashMap<>(distinctExamIds.size() * 2);
        List<ProductRef> products = examAccessReadRepository.findProductsByExamIds(distinctExamIds);
        for (ProductRef product : products) {
            if (product.examId() != null && !resolved.containsKey(product.examId())) {
                resolved.put(
                        product.examId(),
                        new ExamSummary(nonBlankOrNull(product.code()), extractCardName(product.card())));
            }
        }

        for (UUID examId : distinctExamIds) {
            ExamSummary existing = resolved.get(examId);
            if (existing != null && existing.code() != null && existing.name() != null) {
                continue;
            }
            publishedExamPort.findServing(examId)
                    .map(PublishedExamPort.ServingExam::version)
                    .ifPresent(version -> resolved.put(
                            examId,
                            new ExamSummary(
                                    existing != null && existing.code() != null
                                            ? existing.code()
                                            : nonBlankOrNull(version.code()),
                                    existing != null && existing.name() != null
                                            ? existing.name()
                                            : nonBlankOrNull(version.name()))));
        }
        return resolved;
    }

    private String extractCardName(String cardJson) {
        if (cardJson == null || cardJson.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(cardJson);
            JsonNode nameNode = tree.path("name");
            if (nameNode.isTextual()) {
                return nonBlankOrNull(nameNode.asText());
            }
            return null;
        } catch (JsonProcessingException ex) {
            log.debug("Could not extract name from storefront_product.card JSON", ex);
            return null;
        }
    }

    private static String nonBlankOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static EntitlementLearningSource mapSource(EntitlementSource source) {
        return switch (source) {
            case PURCHASE -> EntitlementLearningSource.PURCHASE;
            case CLASS -> EntitlementLearningSource.CLASS;
        };
    }

    private static EntitlementLearningStatus computeStatus(Entitlement entitlement) {
        if (entitlement.attemptsLeft() > 0) {
            return EntitlementLearningStatus.ACTIVE;
        }
        if (entitlement.getAttemptsReserved() > 0) {
            return EntitlementLearningStatus.IN_USE;
        }
        if (entitlement.getAttemptsUsed() == 0 && entitlement.getAttemptsRevoked() > 0) {
            return EntitlementLearningStatus.REVOKED;
        }
        return EntitlementLearningStatus.EXHAUSTED;
    }
}
