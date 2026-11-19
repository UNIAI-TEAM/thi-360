package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.StudentLearningProvider;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * AC-5: the app boots with two real {@link StudentLearningProvider} beans and
 * {@code GET /api/v1/admin/users/{id}/learning} returns the merged data.
 */
class UserLearningMultiProviderIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class LearningProvidersConfig {

        // plan B1: commerce implements the entitlements part.
        @Bean
        @Order(1)
        StudentLearningProvider commerceLearningProvider() {
            return userId -> new StudentLearningProvider.StudentLearningData(
                    true,
                    List.of(Map.of("entitlementId", "E-" + userId)),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(Map.of("ticket", "S-commerce-" + userId)));
        }

        // plan B1: attempt implements the attempts part.
        @Bean
        @Order(2)
        StudentLearningProvider attemptLearningProvider() {
            return userId -> new StudentLearningProvider.StudentLearningData(
                    false,
                    List.of(),
                    List.of(Map.of("attemptId", "A-" + userId)),
                    List.of(),
                    List.of(),
                    List.of(Map.of("ticket", "S-attempt-" + userId)));
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @AfterEach
    void removeUser() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
    }

    @Test
    void learningEndpointMergesBothProviders() throws Exception {
        userId = Ids.newId();
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String userCode = String.format("U%06d", seq);
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId, userCode, "learning.merge." + userId.toString().substring(0, 8),
                ("learning.merge." + userId.toString().substring(0, 8)).toLowerCase(),
                "Learning Merge", userId + "@test.local", (userId + "@test.local").toLowerCase(),
                Timestamp.from(now), userId, Timestamp.from(now), userId);

        mockMvc.perform(get("/api/v1/admin/users/{id}/learning", userId)
                        .with(asUser(Permissions.USER_LEARNING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataAvailable").value(true))
                .andExpect(jsonPath("$.entitlements").isArray())
                .andExpect(jsonPath("$.entitlements.length()").value(1))
                .andExpect(jsonPath("$.entitlements[0].entitlementId").value("E-" + userId))
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].attemptId").value("A-" + userId))
                // Both providers fill supportRequests: the response keeps both entries, in provider order.
                .andExpect(jsonPath("$.supportRequests.length()").value(2))
                .andExpect(jsonPath("$.supportRequests[0].ticket").value("S-commerce-" + userId))
                .andExpect(jsonPath("$.supportRequests[1].ticket").value("S-attempt-" + userId));

    }
}
