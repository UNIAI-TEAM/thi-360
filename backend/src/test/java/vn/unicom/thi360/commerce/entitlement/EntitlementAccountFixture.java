package vn.unicom.thi360.commerce.entitlement;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

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
        CommerceTestFixtures.deleteStubOrdersForUser(jdbc, userId);
        jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
    }
}
