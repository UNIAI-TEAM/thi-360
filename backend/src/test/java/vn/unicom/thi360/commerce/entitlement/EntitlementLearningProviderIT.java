package vn.unicom.thi360.commerce.entitlement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;

class EntitlementLearningProviderIT extends EntitlementIntegrationTest {

    private EntitlementAccountFixture accounts;
    private final List<UUID> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        accounts = new EntitlementAccountFixture(jdbc);
    }

    @AfterEach
    void removeAccounts() {
        createdUsers.forEach(accounts::remove);
    }

    /** AC-24: the admin learning API lists each entitlement with its counters, and dataAvailable is true. */
    @Test
    void learningListsEntitlements() throws Exception {
        UUID userId = account();
        UUID openExam = newId();
        UUID refundedExam = newId();
        GrantOrderResult.Line open = grant(userId, openExam, 4);
        UUID usedAttempt = newId();
        port.reserve(userId, open.entitlementId(), usedAttempt);
        port.confirmUse(usedAttempt);
        port.reserve(userId, open.entitlementId(), newId());
        GrantOrderResult.Line refunded = grant(userId, refundedExam, 2);
        grantService.revokeUnused(refunded.grantId());
        // Another account's entitlement never shows up here.
        grant(account(), newId(), 7);

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", userId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.dataAvailable").value(true))
                .andExpect(jsonPath("$.entitlements", hasSize(2)))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].examId", open.entitlementId())
                        .value(openExam.toString()))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].source", open.entitlementId())
                        .value("PURCHASE"))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsTotal", open.entitlementId())
                        .value(4))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsUsed", open.entitlementId())
                        .value(1))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsReserved",
                        open.entitlementId()).value(1))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsRevoked",
                        open.entitlementId()).value(0))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsLeft", open.entitlementId())
                        .value(2))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].examId", refunded.entitlementId())
                        .value(refundedExam.toString()))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsTotal",
                        refunded.entitlementId()).value(2))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsRevoked",
                        refunded.entitlementId()).value(2))
                .andExpect(jsonPath("$.entitlements[?(@.entitlementId == '%s')].attemptsLeft",
                        refunded.entitlementId()).value(0))
                .andExpect(jsonPath("$.attempts", hasSize(0)));
    }

    /** AC-25: an account with no entitlement gets an empty list and dataAvailable = false. */
    @Test
    void learningStaysEmptyWithoutEntitlement() throws Exception {
        UUID userId = account();
        grant(account(), newId(), 1);

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", userId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataAvailable").value(false))
                .andExpect(jsonPath("$.entitlements", hasSize(0)))
                .andExpect(jsonPath("$.attempts", hasSize(0)))
                .andExpect(jsonPath("$.assessments", hasSize(0)))
                .andExpect(jsonPath("$.practice", hasSize(0)))
                .andExpect(jsonPath("$.supportRequests", hasSize(0)));
    }

    private UUID account() {
        UUID userId = accounts.createStudent("entitlement.learning");
        createdUsers.add(userId);
        return userId;
    }
}
