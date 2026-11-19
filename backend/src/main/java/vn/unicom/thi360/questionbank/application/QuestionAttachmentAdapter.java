package vn.unicom.thi360.questionbank.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Reads the attachments of root versions from their snapshots and, on request, checks the object storage (H48).
 * The metadata is one query that ends before the storage is asked; the adapter does not start a method-wide
 * transaction, so the checker (which runs without an enclosing transaction) does not hold a connection on storage calls.
 */
@Service
public class QuestionAttachmentAdapter implements QuestionAttachmentPort {
    record Row(VersionKey key, UUID assetId, String name, String mime, String objectKey) { }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectStorage storage;

    public QuestionAttachmentAdapter(NamedParameterJdbcTemplate jdbc, ObjectStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Override
    public Map<VersionKey, List<Attachment>> attachments(Collection<VersionKey> versions, boolean verifyStorage) {
        if (versions.isEmpty()) return Map.of();
        var params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();
        int index = 0;
        for (VersionKey key : new LinkedHashSet<>(versions)) {
            clauses.add("(v.question_id = :id" + index + " and v.version_no = :v" + index + ")");
            params.addValue("id" + index, key.questionId()).addValue("v" + index, key.versionNo());
            index++;
        }
        List<Row> rows = jdbc.query("""
                select v.question_id, v.version_no, a.id, a.file_name, a.mime, a.object_key
                from questionbank.question_version v
                cross join lateral jsonb_array_elements_text(
                    coalesce(v.snapshot::jsonb -> 'attachmentIds', '[]'::jsonb)) with ordinality as x(asset_id, position)
                join questionbank.question_asset a on a.id = x.asset_id::uuid and a.kind = 'ATTACHMENT'
                where (
                """ + String.join(" or ", clauses) + """
                )
                order by v.question_id, v.version_no, x.position
                """, params, (rs, n) -> new Row(
                        new VersionKey(rs.getObject("question_id", UUID.class), rs.getInt("version_no")),
                        rs.getObject("id", UUID.class), rs.getString("file_name"), rs.getString("mime"),
                        rs.getString("object_key")));
        if (rows.isEmpty()) return Map.of();
        Map<String, Boolean> stored = new LinkedHashMap<>();
        if (verifyStorage) {
            Set<String> distinct = new LinkedHashSet<>();
            rows.forEach(row -> distinct.add(row.objectKey()));
            for (String objectKey : distinct) stored.put(objectKey, storage.exists(objectKey));
        }
        Map<VersionKey, List<Attachment>> result = new LinkedHashMap<>();
        for (Row row : rows) {
            result.computeIfAbsent(row.key(), ignored -> new ArrayList<>()).add(new Attachment(row.assetId(),
                    row.name(), row.mime(), verifyStorage ? stored.get(row.objectKey()) : null));
        }
        return result;
    }
}
