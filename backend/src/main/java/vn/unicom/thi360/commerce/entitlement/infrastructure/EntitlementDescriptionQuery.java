package vn.unicom.thi360.commerce.entitlement.infrastructure;

import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.GrantSource;

/**
 * Reads what {@code EntitlementPort.describe} returns with one SQL statement, so the owner check, the attempts
 * left and the first usable grant come from one snapshot, and no entitlement or grant entity enters the caller's
 * persistence context (a later {@code reserve} in the same transaction must lock fresh rows).
 */
@Component
public class EntitlementDescriptionQuery {

    /** Owned entitlement and its first usable grant (E21: lowest seq); grant columns are null when none is left. */
    static final String SQL = """
            SELECT e.exam_id,
                   e.attempts_total - e.attempts_used - e.attempts_reserved - e.attempts_revoked AS attempts_left,
                   g.id AS grant_id, g.kind, g.order_code, g.granted_at, g.option_codes
            FROM commerce.entitlement e
            LEFT JOIN LATERAL (
                SELECT x.id, x.kind, x.order_code, x.granted_at, x.option_codes
                FROM commerce.entitlement_grant x
                WHERE x.entitlement_id = e.id
                  AND x.attempts - x.attempts_used - x.attempts_reserved - x.attempts_revoked > 0
                ORDER BY x.seq
                LIMIT 1
            ) g ON true
            WHERE e.id = ? AND e.user_id = ?
            """;

    private final JdbcTemplate jdbc;

    public EntitlementDescriptionQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty when the entitlement does not exist or is not the user's. */
    public Optional<EntitlementDescription> find(UUID userId, UUID entitlementId) {
        return jdbc.query(SQL, (rs, row) -> {
            UUID grantId = rs.getObject("grant_id", UUID.class);
            if (grantId == null) {
                return new EntitlementDescription(
                        rs.getObject("exam_id", UUID.class), rs.getInt("attempts_left"), null, null, null, null,
                        List.of());
            }
            Array optionCodes = rs.getArray("option_codes");
            try {
                return new EntitlementDescription(
                        rs.getObject("exam_id", UUID.class),
                        rs.getInt("attempts_left"),
                        grantId,
                        GrantSource.valueOf(rs.getString("kind")),
                        rs.getString("order_code"),
                        rs.getObject("granted_at", OffsetDateTime.class).toInstant(),
                        Arrays.asList((String[]) optionCodes.getArray()));
            } finally {
                optionCodes.free();
            }
        }, entitlementId, userId).stream().findFirst();
    }
}
