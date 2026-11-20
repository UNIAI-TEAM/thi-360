package vn.unicom.thi360.commerce.order.infrastructure;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;

/**
 * Truy vấn và khóa dòng {@code commerce.storefront_product} phục vụ luồng tạo đơn và tiếp tục thanh toán
 * mà không sửa mã nguồn gói {@code commerce.storefront}.
 */
@Repository
public class StorefrontProductLockRepository {

    private static final String BASE_SELECT = """
            SELECT id, exam_id, exam_item_id, code, name, sale_state, version_no,
                   list_price_vnd, attempts_granted, card::text, detail::text
            FROM commerce.storefront_product
            WHERE code_normalized = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public StorefrontProductLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    public Optional<StoredProduct> lockByNormalizedCode(String codeNormalized) {
        if (codeNormalized == null || codeNormalized.isBlank()) {
            return Optional.empty();
        }
        List<StoredProduct> rows = jdbcTemplate.query(BASE_SELECT + " FOR UPDATE", this::mapRow, codeNormalized);
        return rows.stream().findFirst();
    }

    public Optional<StoredProduct> findByNormalizedCode(String codeNormalized) {
        if (codeNormalized == null || codeNormalized.isBlank()) {
            return Optional.empty();
        }
        List<StoredProduct> rows = jdbcTemplate.query(BASE_SELECT, this::mapRow, codeNormalized);
        return rows.stream().findFirst();
    }

    /** Khóa tập khuyến mãi theo thứ tự UUID tăng dần toàn cục (PostgreSQL unsigned 128-bit) để tránh deadlock (plan B2). */
    public List<UUID> lockPromotionsInOrder(Collection<UUID> promotionIds) {
        if (promotionIds == null || promotionIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = promotionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(
                            "SELECT id FROM commerce.promotion WHERE id = ANY(?) ORDER BY id ASC FOR UPDATE");
                    ps.setArray(1, con.createArrayOf("uuid", distinct.toArray(UUID[]::new)));
                    return ps;
                },
                (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private StoredProduct mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StoredProduct(
                rs.getObject("id", UUID.class),
                rs.getObject("exam_id", UUID.class),
                rs.getObject("exam_item_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                SaleState.valueOf(rs.getString("sale_state")),
                rs.getInt("version_no"),
                rs.getLong("list_price_vnd"),
                rs.getInt("attempts_granted"),
                rs.getString("card"),
                rs.getString("detail"));
    }
}
