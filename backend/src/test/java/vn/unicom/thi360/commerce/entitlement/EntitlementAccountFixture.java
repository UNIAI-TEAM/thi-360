package vn.unicom.thi360.commerce.entitlement;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts and removes a plain active student account directly, as the identity module's own ITs do. */
final class EntitlementAccountFixture {

    private final JdbcTemplate jdbc;

    EntitlementAccountFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    UUID createStudent(String label) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = label + "." + userId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """,
                userId, String.format("U%06d", seq), username, username.toLowerCase(), "Entitlement Test",
                userId + "@test.local", (userId + "@test.local").toLowerCase(), now, userId, now, userId);
        return userId;
    }

    /** Removes the account and every commerce row of it, children first. */
    void remove(UUID userId) {
        jdbc.update("DELETE FROM commerce.entitlement_usage WHERE grant_id IN (SELECT g.id"
                + " FROM commerce.entitlement_grant g JOIN commerce.entitlement e ON e.id = g.entitlement_id"
                + " WHERE e.user_id = ?)", userId);
        jdbc.update("DELETE FROM commerce.entitlement_grant WHERE entitlement_id IN (SELECT id"
                + " FROM commerce.entitlement WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM commerce.entitlement WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
    }
}
