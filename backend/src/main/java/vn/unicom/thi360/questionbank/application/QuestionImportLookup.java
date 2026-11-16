package vn.unicom.thi360.questionbank.application;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemRefView;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.questionbank.domain.importing.ColumnMapping;
import vn.unicom.thi360.questionbank.domain.importing.ExistingQuestionRef;
import vn.unicom.thi360.questionbank.domain.importing.ImportCatalog;
import vn.unicom.thi360.questionbank.domain.importing.ImportCatalogOption;
import vn.unicom.thi360.questionbank.domain.importing.ImportColumnMapper;
import vn.unicom.thi360.questionbank.domain.importing.ImportField;
import vn.unicom.thi360.questionbank.domain.importing.ImportKnowledgeNode;
import vn.unicom.thi360.questionbank.domain.importing.ImportSheet;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Resolves the catalog and claimed source codes in batches before parsing rows. */
@Component
public class QuestionImportLookup {
    private final CatalogLookup catalog;
    private final NamedParameterJdbcTemplate jdbc;

    public QuestionImportLookup(CatalogLookup catalog, NamedParameterJdbcTemplate jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    record Snapshot(ImportCatalog catalog, Map<UUID, String> knowledgeNames) {}

    Snapshot catalog() {
        var knowledge = catalog.listGroupItems("KNOWLEDGE");
        Map<UUID, List<CatalogItemRefView>> refs = catalog.findEffectiveItemRefs(
                knowledge.stream().map(CatalogItemView::id).toList());
        List<ImportKnowledgeNode> nodes = knowledge.stream().map(item -> {
            List<String> subjects = refs.getOrDefault(item.id(), List.of()).stream()
                    .filter(ref -> "SUBJECT".equals(ref.refGroupCode()))
                    .map(CatalogItemRefView::refItemCode).toList();
            return new ImportKnowledgeNode(item.id(), item.code(), item.nameVi(), subjects,
                    item.depth() == 3, item.effectiveStatus() == CatalogStatus.ACTIVE);
        }).toList();
        ImportCatalog result = new ImportCatalog(options("SUBJECT"), options("GRADE"), options("QUESTION_LEVEL"),
                options("TASK_TYPE"), options("QUESTION_TAG"), nodes);
        Map<UUID, String> names = new java.util.HashMap<>();
        knowledge.forEach(item -> names.put(item.id(), item.nameVi()));
        return new Snapshot(result, names);
    }

    private List<ImportCatalogOption> options(String group) {
        return catalog.listGroupItems(group).stream()
                .map(item -> new ImportCatalogOption(item.id(), item.code(), item.nameVi(),
                        item.effectiveStatus() == CatalogStatus.ACTIVE))
                .toList();
    }

    public List<ExistingQuestionRef> existing(ImportSheet sheet, ColumnMapping mapping) {
        String column = ImportColumnMapper.checkMapping(sheet.columns(), mapping)
                .columnByField().get(ImportField.SOURCE_CODE);
        if (column == null) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (var row : sheet.rows()) {
            String value = row.cell(column).trim();
            if (!value.isEmpty()) {
                codes.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return existing(codes);
    }

    private List<ExistingQuestionRef> existing(Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select q.id, q.code, q.type, c.source_code_normalized
                from questionbank.question_source_code_claim c
                join questionbank.question q on q.id = c.question_id
                where c.source_code_normalized in (:codes)
                  and q.parent_id is null and q.deleted_at is null
                """, new MapSqlParameterSource("codes", codes),
                (rs, n) -> new ExistingQuestionRef(rs.getObject("id", UUID.class), rs.getString("code"),
                        QuestionType.valueOf(rs.getString("type")), rs.getString("source_code_normalized")));
    }
}
