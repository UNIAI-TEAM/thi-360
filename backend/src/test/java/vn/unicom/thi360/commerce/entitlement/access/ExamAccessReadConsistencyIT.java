package vn.unicom.thi360.commerce.entitlement.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * How the two exam APIs read: one snapshot per request (entitlements, grants, attempt status and pending orders are read
 * in one read-only {@code REPEATABLE READ} transaction) and one exam-module call per distinct exam.
 */
class ExamAccessReadConsistencyIT extends AbstractExamAccessIT {

    private static final String TRANSACTION_MODE =
            "SELECT current_setting('transaction_isolation') || ',' || current_setting('transaction_read_only')";

    @Test
    void myExamsReadsInOneReadOnlyRepeatableReadTransaction() throws Exception {
        UUID userId = activeAccount();
        grant(userId, onSale().examId(), 1);
        pendingOrders.observeWith(() -> jdbc.queryForObject(TRANSACTION_MODE, String.class));

        myExams(learner(userId));

        assertThat(pendingOrders.observed()).containsExactly("repeatable read,on");
    }

    @Test
    void examAccessQueryReadsInOneReadOnlyRepeatableReadTransaction() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        grant(userId, product.examId(), 1);
        pendingOrders.observeWith(() -> jdbc.queryForObject(TRANSACTION_MODE, String.class));

        queryAccess(learner(userId), product.code());

        assertThat(pendingOrders.observed()).containsExactly("repeatable read,on");
    }

    @Test
    void theSameExamOnSeveralCardsAsksTheExamModuleOnce() throws Exception {
        UUID userId = activeAccount();
        Product owned = onSale();
        Product waiting = onSale();
        grant(userId, owned.examId(), 2);
        pendingOrders.add(userId, "ORD-DUP001", owned.examId());
        pendingOrders.add(userId, "ORD-DUP002", owned.examId());
        pendingOrders.add(userId, "ORD-DUP003", waiting.examId());
        pendingOrders.add(userId, "ORD-DUP004", waiting.examId());

        JsonNode items = myExams(learner(userId)).get("items");

        assertThat(items).as("four checking cards and the entitlement card").hasSize(5);
        assertThat(examPort.servingCalls(owned.examId())).as("three cards of the owned exam").isEqualTo(1);
        assertThat(examPort.servingCalls(waiting.examId())).as("two cards of the awaited exam").isEqualTo(1);
    }
}
