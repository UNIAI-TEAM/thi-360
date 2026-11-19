package vn.unicom.thi360.commerce.entitlement.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttemptState;

/**
 * {@code POST /me/exam-access/query} (TM 10.2): the state of a product for the calling account, by the precedence
 * {@code IN_PROGRESS, CAN_START, CHECKING, EXHAUSTED, NONE}. Plan CM-4b AC-1 to AC-8.
 */
class ExamAccessApiIT extends AbstractExamAccessIT {

    @Test
    void openAttemptWinsOverEverything() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 1);
        UUID attemptId = reserveAttempt(userId, line.entitlementId());
        attempts().open(line.entitlementId(), attemptId, OpenAttemptState.IN_PROGRESS);
        // A stuck paid order does not take the state away from the open attempt either.
        pendingOrders.add(userId, "ORD-STUCK1", product.examId());

        JsonNode item = only(queryAccess(learner(userId), product.code()));

        assertThat(item.get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(item.get("activeAttemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(item.get("attemptsLeft").asInt()).as("the only attempt is held by the open attempt").isZero();
        assertThat(item.get("entitlementId").asText()).isEqualTo(line.entitlementId().toString());
    }

    @Test
    void startingAttemptCountsAsInProgress() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 3);
        UUID attemptId = reserveAttempt(userId, line.entitlementId());
        attempts().open(line.entitlementId(), attemptId, OpenAttemptState.STARTING);

        JsonNode item = only(queryAccess(learner(userId), product.code()));

        assertThat(item.get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(item.get("activeAttemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(item.get("attemptsLeft").asInt()).isEqualTo(2);
    }

    @Test
    void remainingAttemptsCanStart() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 3);
        useAttempt(userId, line.entitlementId());

        JsonNode item = only(queryAccess(learner(userId), product.code()));

        assertThat(item.get("state").asText()).isEqualTo("CAN_START");
        assertThat(item.get("attemptsLeft").asInt()).isEqualTo(2);
        assertThat(item.get("entitlementId").asText()).isEqualTo(line.entitlementId().toString());
        assertThat(item.get("activeAttemptId").isNull()).isTrue();
        assertThat(item.get("hasPendingFulfillment").asBoolean()).isFalse();
        assertThat(item.get("pendingOrderCode").isNull()).isTrue();
    }

    @Test
    void paidOrderWithoutAttemptsIsChecking() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        pendingOrders.add(userId, "ORD-PAID01", product.examId());

        JsonNode item = only(queryAccess(learner(userId), product.code()));

        assertThat(item.get("state").asText()).isEqualTo("CHECKING");
        assertThat(item.get("pendingOrderCode").asText()).isEqualTo("ORD-PAID01");
        assertThat(item.get("hasPendingFulfillment").asBoolean()).isTrue();
        assertThat(item.get("attemptsLeft").asInt()).isZero();
        assertThat(item.get("entitlementId").isNull()).as("nothing is granted yet").isTrue();

        // The same holds when the attempts of an earlier purchase are used up: the paid top-up is what the account waits for.
        UUID other = activeAccount();
        Product used = onSale();
        useAttempt(other, grant(other, used.examId(), 1).entitlementId());
        pendingOrders.add(other, "ORD-PAID02", used.examId());
        assertThat(only(queryAccess(learner(other), used.code())).get("state").asText()).isEqualTo("CHECKING");
    }

    @Test
    void stuckTopUpKeepsCanStart() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 2);
        pendingOrders.add(userId, "ORD-TOPUP1", product.examId());

        JsonNode item = only(queryAccess(learner(userId), product.code()));

        assertThat(item.get("state").asText()).isEqualTo("CAN_START");
        assertThat(item.get("hasPendingFulfillment").asBoolean()).isTrue();
        assertThat(item.get("pendingOrderCode").asText()).isEqualTo("ORD-TOPUP1");
        assertThat(item.get("attemptsLeft").asInt()).isEqualTo(2);
        assertThat(item.get("entitlementId").asText()).isEqualTo(line.entitlementId().toString());
    }

    @Test
    void usedUpIsExhausted() throws Exception {
        UUID userId = activeAccount();
        Product product = onSale();
        var line = grant(userId, product.examId(), 1);
        useAttempt(userId, line.entitlementId());

        JsonNode item = only(queryAccess(learner(userId), product.code()));

        assertThat(item.get("state").asText()).isEqualTo("EXHAUSTED");
        assertThat(item.get("attemptsLeft").asInt()).isZero();
        assertThat(item.get("entitlementId").asText()).isEqualTo(line.entitlementId().toString());
        assertThat(item.get("hasPendingFulfillment").asBoolean()).isFalse();

        // An entitlement whose every attempt was revoked has nothing left either (boundary of the plan).
        UUID refunded = activeAccount();
        Product refundedProduct = onSale();
        var refundedLine = grant(refunded, refundedProduct.examId(), 2);
        grantService.revokeUnused(refundedLine.grantId());
        assertThat(only(queryAccess(learner(refunded), refundedProduct.code())).get("state").asText())
                .isEqualTo("EXHAUSTED");
    }

    @Test
    void noEntitlementOrUnknownCodeIsNone() throws Exception {
        UUID userId = activeAccount();
        Product withoutEntitlement = onSale();
        Product otherExam = onSale();
        UUID someoneElse = activeAccount();
        grant(someoneElse, otherExam.examId(), 3);
        String bundle = insertBundle();

        JsonNode items = queryAccess(learner(userId), withoutEntitlement.code(), otherExam.code(), "NO-SUCH-CODE",
                bundle).get("items");

        assertThat(items).hasSize(4);
        for (JsonNode item : items) {
            assertThat(item.get("state").asText()).as(item.get("code").asText()).isEqualTo("NONE");
            assertThat(item.get("attemptsLeft").asInt()).isZero();
            assertThat(item.get("entitlementId").isNull()).isTrue();
            assertThat(item.get("activeAttemptId").isNull()).isTrue();
            assertThat(item.get("hasPendingFulfillment").asBoolean()).isFalse();
        }
        assertThat(items.get(2).get("code").asText()).isEqualTo("NO-SUCH-CODE");

        // Codes are matched without case and blanks, and answered in the order sent, one row per distinct code.
        Product product = onSale();
        JsonNode padded = queryAccess(learner(userId), "  " + product.code().toLowerCase() + " ", product.code())
                .get("items");
        assertThat(padded).hasSize(1);
        assertThat(padded.get(0).get("code").asText()).isEqualTo(product.code().toLowerCase());
    }

    @Test
    void codesLimitIs48() throws Exception {
        UUID userId = activeAccount();
        RequestPostProcessor who = learner(userId);

        String[] max = IntStream.range(0, 48).mapToObj(i -> "NO-SUCH-" + i).toArray(String[]::new);
        assertThat(queryAccess(who, max).get("items")).hasSize(48);

        mockMvc.perform(post("/api/v1/me/exam-access/query").with(who).contentType(MediaType.APPLICATION_JSON)
                        .content(body(IntStream.range(0, 49).mapToObj(i -> "NO-SUCH-" + i).toArray(String[]::new))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("codes"));

        // The other malformed bodies are 400 as well, never a 500.
        mockMvc.perform(post("/api/v1/me/exam-access/query").with(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/me/exam-access/query").with(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"A\",\" \"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String body(String... codes) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("codes", codes));
    }
}
