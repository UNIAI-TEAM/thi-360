package vn.unicom.thi360.exam.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.catalog.spi.CatalogUsageProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/** Reports catalog references held by exam working structures. */
@Component
public class ExamCatalogUsageProvider implements CatalogUsageProvider {
    private final JdbcTemplate jdbc;
    private final AppMessages messages;

    public ExamCatalogUsageProvider(JdbcTemplate jdbc, AppMessages messages) {
        this.jdbc = jdbc;
        this.messages = messages;
    }

    @Override
    public String source() {
        return messages.get("catalog.usage.source.exam");
    }

    @Override
    public List<CatalogUsage> findUsages(String groupCode, UUID itemId) {
        if (groupCode == null || itemId == null) {
            return List.of();
        }
        String predicate = switch (groupCode) {
            case "EXAM" -> "e.kind_item_id = ?";
            case "SUBJECT" -> "e.subject_item_id = ? OR r.subject_item_id = ?";
            case "SUBJECT_COMBINATION" -> "r.combination_item_id = ?";
            case "KNOWLEDGE" -> "? = ANY(r.knowledge_item_ids)";
            case "QUESTION_TAG" -> "? = ANY(r.tag_item_ids)";
            case "COMPETENCY" -> "? = ANY(r.competency_item_ids)";
            default -> null;
        };
        if (predicate == null) {
            return List.of();
        }
        Object[] args = "SUBJECT".equals(groupCode) ? new Object[] {itemId, itemId} : new Object[] {itemId};
        List<Usage> usages = jdbc.query("SELECT DISTINCT e.id, e.code, e.status FROM exam.exam e "
                        + "LEFT JOIN exam.exam_block_ref r ON r.exam_id = e.id WHERE (" + predicate + ")",
                (rs, row) -> new Usage(rs.getString("code"), rs.getString("status")), args);
        if (usages.isEmpty()) {
            return List.of();
        }
        boolean blocking = usages.stream().anyMatch(u -> !"DISCONTINUED".equals(u.status()));
        return List.of(new CatalogUsage(source(), "EXAM", usages.size(), blocking,
                usages.stream().map(Usage::code).limit(5).toList()));
    }

    private record Usage(String code, String status) {
    }
}
