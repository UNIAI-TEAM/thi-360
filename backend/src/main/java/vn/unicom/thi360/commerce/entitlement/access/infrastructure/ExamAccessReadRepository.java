package vn.unicom.thi360.commerce.entitlement.access.infrastructure;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;

/**
 * Plain SQL reads of {@code exam-access} and {@code /me/exams} (TM 10.2, TM 12): the storefront projection (product code,
 * sale state, prebuilt card) and the entitlement tables of CM-4a. Both live in schema {@code commerce}, so one module
 * owns every table read here; nothing is written.
 */
@Repository
public class ExamAccessReadRepository {

    private final JdbcTemplate jdbc;

    public ExamAccessReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The exam products (never a bundle, CM-6) whose normalised code is in the list. */
    public List<ProductRef> findProductsByCodes(Collection<String> normalizedCodes) {
        if (normalizedCodes.isEmpty()) {
            return List.of();
        }
        return jdbc.query(PRODUCT_SELECT + " WHERE product_type = 'EXAM' AND code_normalized = ANY (?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", normalizedCodes.toArray())),
                (rs, row) -> product(rs));
    }

    /** The exam products of these exams. */
    public List<ProductRef> findProductsByExamIds(Collection<UUID> examIds) {
        if (examIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(PRODUCT_SELECT + " WHERE product_type = 'EXAM' AND exam_id = ANY (?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", examIds.toArray())),
                (rs, row) -> product(rs));
    }

    /** Every purchase entitlement of the account (source {@code PURCHASE}, no source reference), oldest first. */
    public List<EntitlementRow> findPurchaseEntitlements(UUID userId) {
        return jdbc.query("""
                SELECT id, exam_id, attempts_total, attempts_used, attempts_reserved, attempts_revoked
                FROM commerce.entitlement
                WHERE user_id = ? AND source = 'PURCHASE' AND source_ref IS NULL
                ORDER BY created_at, id
                """, (rs, row) -> new EntitlementRow(rs.getObject("id", UUID.class),
                rs.getObject("exam_id", UUID.class), rs.getInt("attempts_total"), rs.getInt("attempts_used"),
                rs.getInt("attempts_reserved"), rs.getInt("attempts_revoked")), userId);
    }

    /**
     * The grants of each entitlement, oldest first (E21: lowest {@code seq} is the FIFO grant): whether a grant still has a
     * usable attempt (the test {@code EntitlementDescriptionQuery} uses) and its purchased option codes (E25).
     */
    public Map<UUID, List<GrantOptions>> findGrantOptions(Collection<UUID> entitlementIds) {
        Map<UUID, List<GrantOptions>> grants = new HashMap<>();
        if (entitlementIds.isEmpty()) {
            return grants;
        }
        jdbc.query("""
                SELECT entitlement_id, option_codes,
                       attempts - attempts_used - attempts_reserved - attempts_revoked > 0 AS usable
                FROM commerce.entitlement_grant
                WHERE entitlement_id = ANY (?)
                ORDER BY entitlement_id, seq
                """,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", entitlementIds.toArray())),
                rs -> {
                    Array array = rs.getArray("option_codes");
                    Set<String> codes = new LinkedHashSet<>();
                    try {
                        for (Object code : (Object[]) array.getArray()) {
                            codes.add((String) code);
                        }
                    } catch (SQLException e) {
                        throw new IllegalStateException("option_codes is not readable", e);
                    } finally {
                        try {
                            array.free();
                        } catch (SQLException ignored) {
                            // the array was read already
                        }
                    }
                    grants.computeIfAbsent(rs.getObject("entitlement_id", UUID.class), id -> new ArrayList<>())
                            .add(new GrantOptions(rs.getBoolean("usable"), codes));
                });
        return grants;
    }

    private static final String PRODUCT_SELECT = """
            SELECT exam_id, code, code_normalized, sale_state, card::text AS card
            FROM commerce.storefront_product""";

    private static ProductRef product(java.sql.ResultSet rs) throws SQLException {
        return new ProductRef(rs.getObject("exam_id", UUID.class), rs.getString("code"),
                rs.getString("code_normalized"), SaleState.valueOf(rs.getString("sale_state")), rs.getString("card"));
    }

    /** One storefront row: the product code of an exam, whether it is still on sale, and its prebuilt card JSON. */
    public record ProductRef(UUID examId, String code, String codeNormalized, SaleState saleState, String card) {
    }

    /** One grant of an entitlement: whether it still has a usable attempt, and the option codes it bought. */
    public record GrantOptions(boolean usable, Set<String> optionCodes) {
    }

    public record EntitlementRow(UUID id, UUID examId, int attemptsTotal, int attemptsUsed, int attemptsReserved,
                                 int attemptsRevoked) {

        public int attemptsLeft() {
            return attemptsTotal - attemptsUsed - attemptsReserved - attemptsRevoked;
        }
    }
}
