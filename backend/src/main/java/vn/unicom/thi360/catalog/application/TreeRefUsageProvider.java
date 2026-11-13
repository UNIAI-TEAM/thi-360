package vn.unicom.thi360.catalog.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.catalog.spi.CatalogUsageProvider;

@Component
public class TreeRefUsageProvider implements CatalogUsageProvider {

    private static final String SOURCE = "Danh mục cây";
    private static final String OBJECT_TYPE = "TREE_NODE";

    private final JdbcTemplate jdbcTemplate;

    public TreeRefUsageProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<CatalogUsage> findUsages(String groupCode, UUID itemId) {
        if (itemId == null) {
            return List.of();
        }
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT cir.item_id)
                FROM catalog.catalog_item_ref cir
                WHERE cir.ref_item_id = ?
                """,
                Long.class,
                itemId);
        if (count == null || count == 0L) {
            return List.of();
        }
        List<String> labels = jdbcTemplate.query(
                """
                SELECT node.code || ' - ' || node.name_vi AS label
                FROM catalog.catalog_item_ref cir
                JOIN catalog.catalog_item node ON node.id = cir.item_id
                WHERE cir.ref_item_id = ?
                ORDER BY node.depth ASC, node.display_order ASC, node.code_normalized ASC
                LIMIT 5
                """,
                (rs, rowNum) -> rs.getString("label"),
                itemId);
        return List.of(new CatalogUsage(SOURCE, OBJECT_TYPE, count, false, labels));
    }
}
