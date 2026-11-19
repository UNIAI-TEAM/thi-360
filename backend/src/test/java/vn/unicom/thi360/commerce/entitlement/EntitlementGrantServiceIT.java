package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementGrant;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementGrantRepository;
import vn.unicom.thi360.commerce.spi.GrantSource;

class EntitlementGrantServiceIT extends EntitlementIntegrationTest {

    @Autowired
    private EntitlementGrantRepository grantRepository;

    /** AC-3: the same order granted twice (IPN retry, worker retry) adds no grant and no attempt. */
    @Test
    void replayedOrderGrantsOnce() {
        UUID userId = newId();
        UUID examA = newId();
        UUID examB = newId();
        GrantOrderCommand command = order(newId(), userId, line(examA, 2), line(examB, 1));

        GrantOrderResult first = grantService.grantForOrder(command);
        GrantOrderResult replay = grantService.grantForOrder(command);

        assertThat(first.lines()).hasSize(2).allSatisfy(l -> assertThat(l.created()).isTrue());
        assertThat(replay.lines()).hasSize(2).allSatisfy(l -> assertThat(l.created()).isFalse());
        for (int i = 0; i < 2; i++) {
            assertThat(replay.lines().get(i).grantId()).isEqualTo(first.lines().get(i).grantId());
            assertThat(replay.lines().get(i).entitlementId()).isEqualTo(first.lines().get(i).entitlementId());
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_grant WHERE order_id = ?", Long.class,
                command.orderId())).isEqualTo(2L);
        assertThat(count(entitlementRow(lineOf(first, examA).entitlementId()), "attempts_total")).isEqualTo(2);
        assertThat(count(entitlementRow(lineOf(first, examB).entitlementId()), "attempts_total")).isEqualTo(1);
        first.lines().forEach(l -> assertCountsEqualGrantSums(l.entitlementId()));
    }

    /** The result lists lines in exam id order, which is random here: find a line by its exam. */
    private static GrantOrderResult.Line lineOf(GrantOrderResult result, UUID examId) {
        return result.lines().stream().filter(l -> l.examId().equals(examId)).findFirst().orElseThrow();
    }

    /** A replay of an order that already granted one of its exams still grants the exam it had not reached. */
    @Test
    void replayGrantsOnlyTheMissingLines() {
        UUID userId = newId();
        UUID examA = newId();
        UUID examB = newId();
        UUID orderId = newId();
        GrantOrderResult partial = grantService.grantForOrder(order(orderId, userId, line(examA, 1)));

        GrantOrderResult full = grantService.grantForOrder(order(orderId, userId, line(examA, 1), line(examB, 4)));

        UUID entitlementA = partial.lines().get(0).entitlementId();
        UUID entitlementB = full.lines().stream().filter(l -> l.examId().equals(examB)).findFirst().orElseThrow()
                .entitlementId();
        assertThat(full.lines().stream().filter(l -> l.examId().equals(examA)).findFirst().orElseThrow().created())
                .isFalse();
        assertThat(count(entitlementRow(entitlementA), "attempts_total")).isEqualTo(1);
        assertThat(count(entitlementRow(entitlementB), "attempts_total")).isEqualTo(4);
        assertCountsEqualGrantSums(entitlementA);
        assertCountsEqualGrantSums(entitlementB);
    }

    /** AC-4: buying again adds to the entitlement; each grant keeps its own snapshot (C13, E21, E25). */
    @Test
    void repurchaseAddsAttemptsAndKeepsSnapshots() {
        UUID userId = newId();
        UUID examId = newId();
        UUID firstOrder = newId();
        UUID secondOrder = newId();

        GrantOrderResult.Line first = grantService.grantForOrder(order(firstOrder, userId,
                new GrantLine(examId, 1, 2, List.of("OPT-A", "OPT-B"), Map.of("ai", true), "FULL")))
                .lines().get(0);
        GrantOrderResult.Line second = grantService.grantForOrder(order(secondOrder, userId,
                new GrantLine(examId, 2, 3, List.of("OPT-C"), Map.of("ai", false), "SCORE_ONLY")))
                .lines().get(0);

        assertThat(second.entitlementId()).isEqualTo(first.entitlementId());
        assertThat(second.grantId()).isNotEqualTo(first.grantId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?", Long.class,
                userId, examId)).isEqualTo(1L);
        assertThat(count(entitlementRow(first.entitlementId()), "attempts_total")).isEqualTo(5);

        EntitlementGrant oldGrant = grantRepository.findById(first.grantId()).orElseThrow();
        EntitlementGrant newGrant = grantRepository.findById(second.grantId()).orElseThrow();
        assertThat(oldGrant.getKind()).isEqualTo(GrantSource.PURCHASE);
        assertThat(oldGrant.getOrderId()).isEqualTo(firstOrder);
        assertThat(oldGrant.getAttempts()).isEqualTo(2);
        assertThat(oldGrant.getOptionCodes()).containsExactly("OPT-A", "OPT-B");
        assertThat(oldGrant.getBenefits()).containsEntry("ai", true);
        assertThat(oldGrant.getResultView()).isEqualTo("FULL");
        assertThat(oldGrant.getExamVersionNo()).isEqualTo(1);
        assertThat(newGrant.getOrderId()).isEqualTo(secondOrder);
        assertThat(newGrant.getAttempts()).isEqualTo(3);
        assertThat(newGrant.getOptionCodes()).containsExactly("OPT-C");
        assertThat(newGrant.getBenefits()).containsEntry("ai", false);
        assertThat(newGrant.getResultView()).isEqualTo("SCORE_ONLY");
        assertThat(newGrant.getExamVersionNo()).isEqualTo(2);
        assertCountsEqualGrantSums(first.entitlementId());
    }

    /** Boundary: a one-attempt grant, and a grant buying more on top of an entitlement with attempts used. */
    @Test
    void singleAttemptGrantIsValid() {
        GrantOrderResult.Line granted = grant(newId(), newId(), 1);

        Map<String, Object> grant = grantRow(granted.grantId());
        assertThat(count(grant, "attempts")).isEqualTo(1);
        assertThat(grant.get("revoked_at")).isNull();
        assertThat(count(entitlementRow(granted.entitlementId()), "attempts_total")).isEqualTo(1);
        assertCountsEqualGrantSums(granted.entitlementId());
    }
}
