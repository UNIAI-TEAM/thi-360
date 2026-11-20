package vn.unicom.thi360.commerce.entitlement.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttemptState;

/**
 * Who may call {@code /me/exam-access/query} and {@code /me/exams} (TM 8.3 step 1, TM 12) and that each account sees only its
 * own data. Plan CM-4b AC-9 to AC-11.
 */
class MyExamsAccessIT extends AbstractExamAccessIT {

    private static final String QUERY_BODY = "{\"codes\":[\"ANY\"]}";

    @Test
    void manageAccountGets403() throws Exception {
        UUID userId = activeAccount();

        expectForbidden(manager(userId));
        expectForbidden(asPlatformAdmin());
    }

    @Test
    void inactiveAndAnonymousAreRejected() throws Exception {
        UUID inactive = account(3);
        UUID deleted = activeAccount();
        jdbc.update("UPDATE identity.user_account SET deleted_at = now() WHERE id = ?", deleted);
        UUID withoutRow = UUID.randomUUID();

        expectForbidden(learner(inactive));
        expectForbidden(learner(deleted));
        expectForbidden(learner(withoutRow));

        mockMvc.perform(get("/api/v1/me/exams")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/exam-access/query").contentType(MediaType.APPLICATION_JSON).content(QUERY_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeLearnerIsServed() throws Exception {
        UUID userId = activeAccount();

        mockMvc.perform(get("/api/v1/me/exams").with(learner(userId))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/exam-access/query").with(learner(userId)).contentType(MediaType.APPLICATION_JSON)
                .content(QUERY_BODY)).andExpect(status().isOk());
    }

    @Test
    void otherAccountSeesNothing() throws Exception {
        UUID owner = activeAccount();
        UUID stranger = activeAccount();
        Product active = onSale();
        Product waiting = onSale();
        var line = grant(owner, active.examId(), 3);
        UUID attemptId = reserveAttempt(owner, line.entitlementId());
        attempts().open(owner, line.entitlementId(), attemptId, OpenAttemptState.IN_PROGRESS);
        attempts().result(owner, line.entitlementId(), UUID.randomUUID());
        pendingOrders.add(owner, "ORD-OWNER1", waiting.examId());

        // The owner sees all of it ...
        JsonNode ownerCards = myExams(learner(owner)).get("items");
        assertThat(ownerCards).hasSize(2);
        JsonNode ownerAccess = queryAccess(learner(owner), active.code(), waiting.code()).get("items");
        assertThat(ownerAccess.get(0).get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(ownerAccess.get(1).get("state").asText()).isEqualTo("CHECKING");

        // ... and another account sees none of it, in either API and whichever way it asks.
        assertThat(myExams(learner(stranger)).get("items")).isEmpty();
        JsonNode strangerAccess = queryAccess(learner(stranger), active.code(), waiting.code()).get("items");
        for (JsonNode item : strangerAccess) {
            assertThat(item.get("state").asText()).isEqualTo("NONE");
            assertThat(item.get("attemptsLeft").asInt()).isZero();
            assertThat(item.get("entitlementId").isNull()).isTrue();
            assertThat(item.get("activeAttemptId").isNull()).isTrue();
            assertThat(item.get("hasPendingFulfillment").asBoolean()).isFalse();
            assertThat(item.get("pendingOrderCode").isNull()).isTrue();
        }
        assertThat(strangerAccess).hasSize(2);
    }

    private void expectForbidden(RequestPostProcessor who) throws Exception {
        mockMvc.perform(get("/api/v1/me/exams").with(who))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/me/exam-access/query").with(who).contentType(MediaType.APPLICATION_JSON)
                        .content(QUERY_BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
