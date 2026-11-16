package vn.unicom.thi360.questionbank.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.catalog.spi.CatalogUsageProvider;

/**
 * Báo cho danh mục biết một giá trị đang được câu hỏi dùng (spec 7: đếm sử dụng trước khi
 * ngừng giá trị). Không chặn ({@code blocking = false}) — spec 7 cho phép ngừng giá trị sau
 * cảnh báo, câu đang dùng giữ nguyên.
 */
@Component
public class QuestionCatalogUsageProvider implements CatalogUsageProvider {

    private static final String SOURCE = "questionbank";
    private static final String OBJECT_TYPE = "QUESTION";

    /** Nhóm danh mục → cách đếm: cột trực tiếp, hoặc bảng liên kết. */
    private static final Map<String, String> COLUMN_BY_GROUP = Map.of(
            "SUBJECT", "subject_item_id",
            "GRADE", "grade_item_id",
            "QUESTION_LEVEL", "level_item_id",
            "TASK_TYPE", "task_type_item_id",
            "COMPETENCY", "competency_item_id");

    private final JdbcTemplate jdbcTemplate;

    public QuestionCatalogUsageProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<CatalogUsage> findUsages(String groupCode, UUID itemId) {
        if (groupCode == null || itemId == null) {
            return List.of();
        }
        String column = COLUMN_BY_GROUP.get(groupCode);
        if (column != null) {
            return usageOf(
                    "SELECT COUNT(*) FROM questionbank.question q WHERE q.deleted_at IS NULL"
                            + " AND q." + column + " = ?",
                    "SELECT q.code FROM questionbank.question q WHERE q.deleted_at IS NULL"
                            + " AND q." + column + " = ? ORDER BY q.code LIMIT 5",
                    itemId);
        }
        return switch (groupCode) {
            case "QUESTION_TAG" -> usageOf(
                    "SELECT COUNT(*) FROM questionbank.question_tag t"
                            + " JOIN questionbank.question q ON q.id = t.question_id"
                            + " WHERE q.deleted_at IS NULL AND t.tag_item_id = ?",
                    "SELECT q.code FROM questionbank.question_tag t"
                            + " JOIN questionbank.question q ON q.id = t.question_id"
                            + " WHERE q.deleted_at IS NULL AND t.tag_item_id = ?"
                            + " ORDER BY q.code LIMIT 5",
                    itemId);
            case "KNOWLEDGE" -> usageOf(
                    "SELECT COUNT(*) FROM questionbank.question_knowledge k"
                            + " JOIN questionbank.question q ON q.id = k.question_id"
                            + " WHERE q.deleted_at IS NULL AND k.knowledge_item_id = ?",
                    "SELECT q.code FROM questionbank.question_knowledge k"
                            + " JOIN questionbank.question q ON q.id = k.question_id"
                            + " WHERE q.deleted_at IS NULL AND k.knowledge_item_id = ?"
                            + " ORDER BY q.code LIMIT 5",
                    itemId);
            default -> List.of();
        };
    }

    private List<CatalogUsage> usageOf(String countSql, String labelsSql, UUID itemId) {
        Long count = jdbcTemplate.queryForObject(countSql, Long.class, itemId);
        if (count == null || count == 0L) {
            return List.of();
        }
        List<String> labels = jdbcTemplate.query(labelsSql, (rs, n) -> rs.getString(1), itemId);
        return List.of(new CatalogUsage(SOURCE, OBJECT_TYPE, count, false, labels));
    }
}
