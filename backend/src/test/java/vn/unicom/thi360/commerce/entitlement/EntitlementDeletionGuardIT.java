package vn.unicom.thi360.commerce.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.identity.spi.Permissions;

class EntitlementDeletionGuardIT extends EntitlementIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

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

    /** AC-22: an account with an entitlement is not deletable: deletion-check says so, DELETE is 409. */
    @Test
    void accountWithEntitlementCannotBeDeleted() throws Exception {
        UUID userId = account();
        grant(userId, newId(), 1);

        mockMvc.perform(get("/api/v1/admin/users/{id}/deletion-check", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(false))
                .andExpect(jsonPath("$.blockers.length()").value(1))
                .andExpect(jsonPath("$.blockers[0].source").value("COMMERCE"))
                .andExpect(jsonPath("$.blockers[0].code").value("USER_HAS_TRANSACTIONS"))
                .andExpect(jsonPath("$.blockers[0].message").isNotEmpty());

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .param("reason", "Yêu cầu xóa")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_TRANSACTIONS"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("COMMERCE"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("USER_HAS_TRANSACTIONS"));

        assertThat(jdbc.queryForObject(
                "SELECT deleted_at FROM identity.user_account WHERE id = ?", Object.class, userId)).isNull();
    }

    /** AC-23: an account without any entitlement is not blocked by this guard, and a delete goes through. */
    @Test
    void accountWithoutEntitlementIsDeletable() throws Exception {
        UUID userId = account();
        // Entitlements of other accounts do not block this one.
        grant(account(), newId(), 1);

        mockMvc.perform(get("/api/v1/admin/users/{id}/deletion-check", userId)
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletable").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .param("reason", "Người dùng yêu cầu")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT deleted_at FROM identity.user_account WHERE id = ?", Object.class, userId)).isNotNull();
    }

    /**
     * Plan "Ảnh hưởng ngoài task": the guard runs once per selected account on a bulk delete, so its lookup must
     * be an index lookup on {@code user_id}. With sequential scans disabled the planner can only answer through
     * an index, and {@code uq_entitlement_scope} (leading column {@code user_id}) must be that index.
     */
    @Test
    void guardLookupUsesAnIndexLedByUserId() {
        UUID userId = account();
        grant(userId, newId(), 1);

        String plan = new TransactionTemplate(transactionManager).execute(status -> jdbc.execute(
                (java.sql.Connection connection) -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET LOCAL enable_seqscan = off");
                        StringBuilder text = new StringBuilder();
                        try (ResultSet rows = statement.executeQuery(
                                "EXPLAIN SELECT e.id FROM commerce.entitlement e WHERE e.user_id = '"
                                        + userId + "' FETCH FIRST 1 ROWS ONLY")) {
                            while (rows.next()) {
                                text.append(rows.getString(1)).append('\n');
                            }
                        }
                        return text.toString();
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }));

        assertThat(plan).contains("uq_entitlement_scope").doesNotContain("Seq Scan");
    }

    private UUID account() {
        UUID userId = accounts.createStudent("entitlement.guard");
        createdUsers.add(userId);
        return userId;
    }
}
