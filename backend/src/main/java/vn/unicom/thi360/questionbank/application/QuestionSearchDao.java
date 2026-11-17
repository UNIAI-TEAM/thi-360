package vn.unicom.thi360.questionbank.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.questionbank.domain.QuestionSearchNormalizer;
import vn.unicom.thi360.questionbank.domain.QuestionStatus;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;

/**
 * Truy vấn danh sách câu hỏi (spec 4.5): chỉ câu độc lập chưa xóa; tìm kiếm ILIKE trên
 * {@code search_text} (đã bỏ dấu, chỉ mục GIN trigram — plan B5), từ khóa &lt; 2 ký tự chỉ
 * so mã câu. Bộ lọc nhận id giá trị danh mục đã quy đổi (lớp ứng dụng tra mã qua
 * {@code CatalogLookup}); lọc MAPID nhận cả nút con đã mở rộng (D23).
 */
@Component
public class QuestionSearchDao {

    /** Trường sort cho phép của danh sách (plan B5). */
    public static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "code", "points", "type", "status");

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "q.created_at",
            "updatedAt", "q.updated_at",
            "code", "q.code",
            "points", "q.points",
            "type", "q.type",
            "status", "q.status");

    /** Bộ lọc danh sách; mọi trường nullable. */
    public record QuestionFilter(
            String keyword,
            UUID subjectItemId,
            UUID gradeItemId,
            UUID levelItemId,
            String type,
            UUID taskTypeItemId,
            List<UUID> knowledgeItemIds,
            List<UUID> tagItemIds,
            QuestionStatus status,
            UUID createdBy,
            LocalDate createdFrom,
            LocalDate createdTo,
            Boolean hasSolution,
            UUID competencyItemId) {}

    private final JdbcTemplate jdbcTemplate;

    public QuestionSearchDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Một dòng của danh sách, đủ trường cho {@code QuestionListItem}. */
    public record QuestionRow(
            UUID id,
            String code,
            String type,
            String status,
            String contentText,
            UUID subjectItemId,
            UUID gradeItemId,
            UUID levelItemId,
            UUID taskTypeItemId,
            UUID competencyItemId,
            java.math.BigDecimal points,
            boolean hasSolution,
            Integer currentVersionNo,
            Integer workingVersionNo,
            Long revision,
            String sourceCode,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt) {}

    public PageResponse<QuestionRow> search(QuestionFilter filter, PageQuery pageQuery) {
        var where = new StringBuilder("q.parent_id IS NULL AND q.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        appendFilters(where, params, filter, true);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questionbank.question q WHERE " + where,
                Long.class, params.toArray());
        long totalElements = total == null ? 0L : total;

        String orderBy = orderByClause(pageQuery.sort());
        String sql = """
                SELECT q.id, q.code, q.type, q.status, q.content_text,
                       q.subject_item_id, q.grade_item_id, q.level_item_id,
                       q.task_type_item_id, q.competency_item_id, q.points, q.has_solution,
                       q.current_version_no, q.working_version_no, q.revision,
                       q.source_code, q.created_by, q.created_at, q.updated_at
                FROM questionbank.question q
                WHERE %s
                ORDER BY %s, q.id
                LIMIT ? OFFSET ?
                """.formatted(where, orderBy);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageQuery.size());
        pageParams.add((long) pageQuery.page() * pageQuery.size());

        List<QuestionRow> rows = jdbcTemplate.query(sql, (rs, n) -> new QuestionRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("type"),
                rs.getString("status"),
                rs.getString("content_text"),
                rs.getObject("subject_item_id", UUID.class),
                rs.getObject("grade_item_id", UUID.class),
                rs.getObject("level_item_id", UUID.class),
                rs.getObject("task_type_item_id", UUID.class),
                rs.getObject("competency_item_id", UUID.class),
                rs.getBigDecimal("points"),
                rs.getBoolean("has_solution"),
                (Integer) rs.getObject("current_version_no"),
                (Integer) rs.getObject("working_version_no"),
                (Long) rs.getObject("revision"),
                rs.getString("source_code"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), pageParams.toArray());

        return new PageResponse<>(
                rows, pageQuery.page(), pageQuery.size(), totalElements,
                (int) Math.ceil((double) totalElements / pageQuery.size()));
    }

    /** Số câu theo trạng thái với cùng bộ lọc (bỏ qua tham số {@code status} của filter). */
    public Map<QuestionStatus, Long> statusCounts(QuestionFilter filter) {
        var where = new StringBuilder("q.parent_id IS NULL AND q.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        appendFilters(where, params, filter, false);

        Map<QuestionStatus, Long> counts = new HashMap<>();
        for (QuestionStatus status : QuestionStatus.values()) {
            counts.put(status, 0L);
        }
        jdbcTemplate.queryForList(
                        "SELECT q.status, count(*) AS n FROM questionbank.question q WHERE " + where
                                + " GROUP BY q.status",
                        params.toArray())
                .forEach(row -> counts.put(
                        QuestionStatus.valueOf((String) row.get("status")),
                        ((Number) row.get("n")).longValue()));
        return counts;
    }

    private void appendFilters(
            StringBuilder where, List<Object> params, QuestionFilter filter, boolean includeStatus) {
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            String keyword = QuestionSearchNormalizer.normalize(filter.keyword().trim());
            if (keyword.length() < 2) {
                // Từ khóa dưới 2 ký tự chỉ so mã (plan B5).
                where.append(" AND q.code ILIKE ?");
                params.add("%" + filter.keyword().trim() + "%");
            } else {
                where.append(" AND (q.search_text ILIKE ? OR q.code ILIKE ?)");
                params.add("%" + keyword + "%");
                params.add("%" + filter.keyword().trim() + "%");
            }
        }
        eq(where, params, "q.subject_item_id", filter.subjectItemId());
        eq(where, params, "q.grade_item_id", filter.gradeItemId());
        eq(where, params, "q.level_item_id", filter.levelItemId());
        eq(where, params, "q.task_type_item_id", filter.taskTypeItemId());
        eq(where, params, "q.competency_item_id", filter.competencyItemId());
        if (filter.type() != null) {
            where.append(" AND q.type = ?");
            params.add(filter.type());
        }
        if (includeStatus && filter.status() != null) {
            where.append(" AND q.status = ?");
            params.add(filter.status().name());
        }
        if (filter.knowledgeItemIds() != null && !filter.knowledgeItemIds().isEmpty()) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM questionbank.question_knowledge qk
                                 WHERE qk.question_id = q.id AND qk.knowledge_item_id IN (%s))
                    """.formatted(placeholders(filter.knowledgeItemIds().size())));
            params.addAll(filter.knowledgeItemIds());
        }
        if (filter.tagItemIds() != null && !filter.tagItemIds().isEmpty()) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM questionbank.question_tag qt
                                 WHERE qt.question_id = q.id AND qt.tag_item_id IN (%s))
                    """.formatted(placeholders(filter.tagItemIds().size())));
            params.addAll(filter.tagItemIds());
        }
        eq(where, params, "q.created_by", filter.createdBy());
        if (filter.createdFrom() != null) {
            where.append(" AND q.created_at >= ?");
            params.add(filter.createdFrom().atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        if (filter.createdTo() != null) {
            where.append(" AND q.created_at < ?");
            params.add(filter.createdTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        if (filter.hasSolution() != null) {
            where.append(" AND q.has_solution = ?");
            params.add(filter.hasSolution());
        }
    }

    private static void eq(StringBuilder where, List<Object> params, String column, UUID value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static String orderByClause(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "q.created_at DESC";
        }
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column != null) {
                parts.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        return parts.isEmpty() ? "q.created_at DESC" : String.join(", ", parts);
    }
}
