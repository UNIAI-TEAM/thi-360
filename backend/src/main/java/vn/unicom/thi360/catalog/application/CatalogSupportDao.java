package vn.unicom.thi360.catalog.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.api.CatalogGroupRefResponse;
import vn.unicom.thi360.catalog.api.CatalogInheritedRefResponse;
import vn.unicom.thi360.catalog.api.CatalogItemRefResponse;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Component
public class CatalogSupportDao {

    private final JdbcTemplate jdbcTemplate;

    public CatalogSupportDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<UUID, List<CatalogGroupRefResponse>> loadGroupRefs(Collection<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(groupIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT r.group_id,
                       r.ref_group_id,
                       rg.code,
                       rg.name_vi,
                       rg.name_en,
                       rg.status,
                       r.position,
                       r.is_default,
                       (SELECT COUNT(DISTINCT cir.item_id)
                        FROM catalog.catalog_item_ref cir
                        JOIN catalog.catalog_item src ON src.id = cir.item_id
                        JOIN catalog.catalog_item ref_item ON ref_item.id = cir.ref_item_id
                        WHERE src.group_id = r.group_id
                          AND ref_item.group_id = r.ref_group_id) AS linked_item_count
                FROM catalog.catalog_group_ref r
                JOIN catalog.catalog_group rg ON rg.id = r.ref_group_id
                WHERE r.group_id IN (%s)
                ORDER BY r.position ASC, rg.code_normalized ASC
                """.formatted(placeholders);
        Map<UUID, List<CatalogGroupRefResponse>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID gid = rs.getObject("group_id", UUID.class);
                    CatalogGroupRefResponse ref = new CatalogGroupRefResponse(
                            rs.getObject("ref_group_id", UUID.class),
                            rs.getString("code"),
                            rs.getString("name_vi"),
                            rs.getString("name_en"),
                            CatalogStatus.fromCode(rs.getShort("status")),
                            rs.getInt("position"),
                            rs.getBoolean("is_default"),
                            rs.getLong("linked_item_count"));
                    result.computeIfAbsent(gid, ignored -> new ArrayList<>()).add(ref);
                },
                ids.toArray());
        return result;
    }

    public Map<UUID, Long> countDirectChildren(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT parent_id, COUNT(*) AS cnt
                FROM catalog.catalog_item
                WHERE parent_id IN (%s)
                GROUP BY parent_id
                """.formatted(placeholders);
        Map<UUID, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    counts.put(rs.getObject("parent_id", UUID.class), rs.getLong("cnt"));
                },
                ids.toArray());
        return counts;
    }

    public Map<UUID, Long> countActiveDirectChildren(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT parent_id, COUNT(*) AS cnt
                FROM catalog.catalog_item
                WHERE parent_id IN (%s)
                  AND status = 1
                GROUP BY parent_id
                """.formatted(placeholders);
        Map<UUID, Long> counts = new HashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    counts.put(rs.getObject("parent_id", UUID.class), rs.getLong("cnt"));
                },
                ids.toArray());
        return counts;
    }

    public Set<UUID> findInactiveEffectiveItemIds(UUID groupId, Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT i.id
                FROM catalog.catalog_item i
                JOIN catalog.catalog_group g ON g.id = i.group_id
                WHERE i.group_id = ?
                  AND i.id IN (%s)
                  AND (
                      g.status <> 1
                      OR EXISTS (
                          SELECT 1
                          FROM catalog.catalog_item anc
                          WHERE anc.group_id = i.group_id
                            AND i.path LIKE anc.path || '%%'
                            AND anc.status <> 1
                      )
                  )
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(ids.size() + 1);
        params.add(groupId);
        params.addAll(ids);
        List<UUID> inactive = jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getObject("id", UUID.class), params.toArray());
        return Set.copyOf(inactive);
    }

    public Map<UUID, List<CatalogItemRefResponse>> loadOwnRefs(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT cir.item_id,
                       ri.id AS ref_item_id,
                       rg.id AS ref_group_id,
                       rg.code AS ref_group_code,
                       rg.name_vi AS ref_group_name_vi,
                       rg.name_en AS ref_group_name_en,
                       ri.code AS ref_code,
                       ri.name_vi AS ref_name_vi,
                       ri.name_en AS ref_name_en,
                       ri.status AS ref_status
                FROM catalog.catalog_item_ref cir
                JOIN catalog.catalog_item src ON src.id = cir.item_id
                JOIN catalog.catalog_item ri ON ri.id = cir.ref_item_id
                JOIN catalog.catalog_group rg ON rg.id = ri.group_id
                LEFT JOIN catalog.catalog_group_ref cgr
                  ON cgr.group_id = src.group_id AND cgr.ref_group_id = rg.id
                WHERE cir.item_id IN (%s)
                ORDER BY COALESCE(cgr.position, 9999) ASC, ri.display_order ASC, ri.code_normalized ASC
                """.formatted(placeholders);
        Map<UUID, List<CatalogItemRefResponse>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID itemId = rs.getObject("item_id", UUID.class);
                    CatalogItemRefResponse ref = new CatalogItemRefResponse(
                            rs.getObject("ref_item_id", UUID.class),
                            rs.getObject("ref_group_id", UUID.class),
                            rs.getString("ref_group_code"),
                            rs.getString("ref_group_name_vi"),
                            rs.getString("ref_group_name_en"),
                            rs.getString("ref_code"),
                            rs.getString("ref_name_vi"),
                            rs.getString("ref_name_en"),
                            CatalogStatus.fromCode(rs.getShort("ref_status")));
                    result.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(ref);
                },
                ids.toArray());
        return result;
    }

    public Map<UUID, List<CatalogInheritedRefResponse>> loadInheritedRefs(
            UUID groupId, Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT n.id AS item_id,
                       ri.id AS ref_item_id,
                       rg.id AS ref_group_id,
                       rg.code AS ref_group_code,
                       rg.name_vi AS ref_group_name_vi,
                       rg.name_en AS ref_group_name_en,
                       ri.code AS ref_code,
                       ri.name_vi AS ref_name_vi,
                       ri.name_en AS ref_name_en,
                       ri.status AS ref_status,
                       anc.id AS from_item_id,
                       anc.code AS from_item_code,
                       anc.name_vi AS from_item_name_vi,
                       anc.name_en AS from_item_name_en
                FROM catalog.catalog_item n
                JOIN catalog.catalog_item anc
                  ON anc.group_id = n.group_id
                 AND anc.id <> n.id
                 AND n.path LIKE anc.path || '%%'
                JOIN catalog.catalog_item_ref cir ON cir.item_id = anc.id
                JOIN catalog.catalog_item ri ON ri.id = cir.ref_item_id
                JOIN catalog.catalog_group rg ON rg.id = ri.group_id
                LEFT JOIN catalog.catalog_group_ref cgr
                  ON cgr.group_id = n.group_id AND cgr.ref_group_id = rg.id
                WHERE n.group_id = ?
                  AND n.id IN (%s)
                ORDER BY anc.depth ASC, COALESCE(cgr.position, 9999) ASC, ri.display_order ASC, ri.code_normalized ASC
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(ids.size() + 1);
        params.add(groupId);
        params.addAll(ids);
        Map<UUID, List<CatalogInheritedRefResponse>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID itemId = rs.getObject("item_id", UUID.class);
                    CatalogInheritedRefResponse ref = new CatalogInheritedRefResponse(
                            rs.getObject("ref_item_id", UUID.class),
                            rs.getObject("ref_group_id", UUID.class),
                            rs.getString("ref_group_code"),
                            rs.getString("ref_group_name_vi"),
                            rs.getString("ref_group_name_en"),
                            rs.getString("ref_code"),
                            rs.getString("ref_name_vi"),
                            rs.getString("ref_name_en"),
                            CatalogStatus.fromCode(rs.getShort("ref_status")),
                            rs.getObject("from_item_id", UUID.class),
                            rs.getString("from_item_code"),
                            rs.getString("from_item_name_vi"),
                            rs.getString("from_item_name_en"));
                    result.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(ref);
                },
                params.toArray());
        return result;
    }

    public record RefFilterCriteria(List<UUID> refItemIds, int distinctGroupCount, boolean impossible) {
    }

    public RefFilterCriteria resolveRefFilter(UUID treeGroupId, List<UUID> rawRefItemIds) {
        if (rawRefItemIds == null || rawRefItemIds.isEmpty()) {
            return new RefFilterCriteria(List.of(), 0, false);
        }
        List<UUID> uniqueIds = List.copyOf(new LinkedHashSet<>(rawRefItemIds));
        String placeholders = String.join(",", Collections.nCopies(uniqueIds.size(), "?"));
        String sql =
                """
                SELECT i.id, i.group_id, (cgr.ref_group_id IS NOT NULL) AS declared
                FROM catalog.catalog_item i
                LEFT JOIN catalog.catalog_group_ref cgr
                  ON cgr.group_id = ? AND cgr.ref_group_id = i.group_id
                WHERE i.id IN (%s)
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(uniqueIds.size() + 1);
        params.add(treeGroupId);
        params.addAll(uniqueIds);

        Set<UUID> foundIds = new LinkedHashSet<>();
        Set<UUID> groupIds = new LinkedHashSet<>();
        boolean[] hasUndeclared = {false};
        jdbcTemplate.query(
                sql,
                rs -> {
                    foundIds.add(rs.getObject("id", UUID.class));
                    if (!rs.getBoolean("declared")) {
                        hasUndeclared[0] = true;
                    } else {
                        groupIds.add(rs.getObject("group_id", UUID.class));
                    }
                },
                params.toArray());

        if (hasUndeclared[0] || foundIds.size() != uniqueIds.size() || groupIds.isEmpty()) {
            return new RefFilterCriteria(List.of(), 0, true);
        }
        return new RefFilterCriteria(uniqueIds, groupIds.size(), false);
    }
}
