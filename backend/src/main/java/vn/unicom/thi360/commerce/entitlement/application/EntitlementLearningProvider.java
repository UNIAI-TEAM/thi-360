package vn.unicom.thi360.commerce.entitlement.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.identity.spi.StudentLearningProvider;

/**
 * The {@code entitlements} part of the learning profile (plan B1: one provider bean per module; the attempt
 * module supplies {@code attempts}). An account without any entitlement has no data from this module, so
 * {@code dataAvailable} stays {@code false} for it.
 */
@Component
public class EntitlementLearningProvider implements StudentLearningProvider {

    private final EntitlementRepository entitlementRepository;

    public EntitlementLearningProvider(EntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLearningData loadForUser(UUID userId) {
        List<Entitlement> entitlements = entitlementRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        if (entitlements.isEmpty()) {
            return StudentLearningData.empty();
        }
        List<Object> rows = entitlements.stream()
                .<Object>map(e -> new EntitlementLearningRow(
                        e.getId(),
                        e.getExamId(),
                        e.getSource(),
                        e.getAttemptsTotal(),
                        e.getAttemptsUsed(),
                        e.getAttemptsReserved(),
                        e.getAttemptsRevoked(),
                        e.attemptsLeft()))
                .toList();
        return new StudentLearningData(true, rows, List.of(), List.of(), List.of(), List.of());
    }
}
