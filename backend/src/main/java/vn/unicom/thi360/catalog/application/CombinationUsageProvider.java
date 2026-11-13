package vn.unicom.thi360.catalog.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.domain.CatalogNormalizer;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.catalog.spi.CatalogUsageProvider;

@Component
public class CombinationUsageProvider implements CatalogUsageProvider {

    private static final String SOURCE = "Mã tổ hợp";
    private static final String OBJECT_TYPE = "SUBJECT_COMBINATION";

    private final JdbcTemplate jdbcTemplate;

    public CombinationUsageProvider(JdbcTemplate jdbcTemplate) {
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
        String normalized = CatalogNormalizer.normalizeCode(groupCode);
        if ("exam".equals(normalized)) {
            return queryByExam(itemId);
        }
        if ("subject".equals(normalized)) {
            return queryBySubject(itemId);
        }
        if (normalized == null) {
            List<CatalogUsage> result = new ArrayList<>();
            result.addAll(queryByExam(itemId));
            result.addAll(queryBySubject(itemId));
            return result;
        }
        return List.of();
    }

    private List<CatalogUsage> queryByExam(UUID examItemId) {
        UsageSummary summary = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT cv.item_id) AS total_cnt,
                       COUNT(DISTINCT CASE WHEN cv.is_current = true AND ci.status = 1 THEN cv.item_id END) AS blocking_cnt
                FROM catalog.combination_version cv
                JOIN catalog.catalog_item ci ON ci.id = cv.item_id
                WHERE cv.exam_item_id = ?
                """,
                (rs, rowNum) -> new UsageSummary(rs.getLong("total_cnt"), rs.getLong("blocking_cnt")),
                examItemId);
        if (summary == null || summary.totalCount() == 0L) {
            return List.of();
        }
        List<String> labels = jdbcTemplate.query(
                """
                SELECT sub.label
                FROM (
                    SELECT ci.code || ' - ' || ci.name_vi AS label,
                           MAX(CASE WHEN cv.is_current = true AND ci.status = 1 THEN 1 ELSE 0 END) AS is_blocking,
                           MIN(ci.display_order) AS disp,
                           ci.code_normalized
                    FROM catalog.combination_version cv
                    JOIN catalog.catalog_item ci ON ci.id = cv.item_id
                    WHERE cv.exam_item_id = ?
                    GROUP BY ci.id, ci.code, ci.name_vi, ci.code_normalized
                ) sub
                ORDER BY sub.is_blocking DESC, sub.disp ASC, sub.code_normalized ASC
                LIMIT 5
                """,
                (rs, rowNum) -> rs.getString("label"),
                examItemId);
        return List.of(new CatalogUsage(
                SOURCE, OBJECT_TYPE, summary.totalCount(), summary.blockingCount() > 0L, labels));
    }

    private List<CatalogUsage> queryBySubject(UUID subjectItemId) {
        UsageSummary summary = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT cv.item_id) AS total_cnt,
                       COUNT(DISTINCT CASE WHEN cv.is_current = true AND ci.status = 1 THEN cv.item_id END) AS blocking_cnt
                FROM catalog.combination_subject cs
                JOIN catalog.combination_version cv ON cv.id = cs.combination_version_id
                JOIN catalog.catalog_item ci ON ci.id = cv.item_id
                WHERE cs.subject_item_id = ?
                """,
                (rs, rowNum) -> new UsageSummary(rs.getLong("total_cnt"), rs.getLong("blocking_cnt")),
                subjectItemId);
        if (summary == null || summary.totalCount() == 0L) {
            return List.of();
        }
        List<String> labels = jdbcTemplate.query(
                """
                SELECT sub.label
                FROM (
                    SELECT ci.code || ' - ' || ci.name_vi AS label,
                           MAX(CASE WHEN cv.is_current = true AND ci.status = 1 THEN 1 ELSE 0 END) AS is_blocking,
                           MIN(ci.display_order) AS disp,
                           ci.code_normalized
                    FROM catalog.combination_subject cs
                    JOIN catalog.combination_version cv ON cv.id = cs.combination_version_id
                    JOIN catalog.catalog_item ci ON ci.id = cv.item_id
                    WHERE cs.subject_item_id = ?
                    GROUP BY ci.id, ci.code, ci.name_vi, ci.code_normalized
                ) sub
                ORDER BY sub.is_blocking DESC, sub.disp ASC, sub.code_normalized ASC
                LIMIT 5
                """,
                (rs, rowNum) -> rs.getString("label"),
                subjectItemId);
        return List.of(new CatalogUsage(
                SOURCE, OBJECT_TYPE, summary.totalCount(), summary.blockingCount() > 0L, labels));
    }

    private record UsageSummary(long totalCount, long blockingCount) {
    }
}
