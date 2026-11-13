package vn.unicom.thi360.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditQueryCriteria;

@Repository
public class AuditStoreRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "occurredAt", "occurred_at",
            "action", "action",
            "module", "module");

    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditPartitionManager partitionManager;
    private final ObjectMapper objectMapper;

    public AuditStoreRepository(
            NamedParameterJdbcTemplate namedJdbc,
            AuditPartitionManager partitionManager,
            ObjectMapper objectMapper) {
        this.namedJdbc = namedJdbc;
        this.partitionManager = partitionManager;
        this.objectMapper = objectMapper;
    }

    public boolean insertIdempotent(AuditOutboxPayload payload) {
        partitionManager.ensureQuarterPartitionFor(payload.occurredAt());

        String[] rolesArray = payload.actorRoles() == null
                ? new String[0]
                : payload.actorRoles().toArray(String[]::new);
        SqlTypeValue rolesSqlValue = (ps, paramIndex, sqlType, typeName) -> {
            Array array = ps.getConnection().createArrayOf("text", rolesArray);
            ps.setArray(paramIndex, array);
        };

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", payload.id())
                .addValue("occurredAt", Timestamp.from(payload.occurredAt()))
                .addValue("actorId", payload.actorId())
                .addValue("actorUsername", payload.actorUsername())
                .addValue("actorRoles", rolesSqlValue, Types.ARRAY)
                .addValue("actorType", payload.actorType().code())
                .addValue("module", payload.module())
                .addValue("action", payload.action())
                .addValue("targetType", payload.targetType())
                .addValue("targetId", payload.targetId())
                .addValue("targetLabel", payload.targetLabel())
                .addValue("before", toJson(payload.before()), Types.VARCHAR)
                .addValue("after", toJson(payload.after()), Types.VARCHAR)
                .addValue("reason", payload.reason())
                .addValue("outcome", payload.outcome().code())
                .addValue("errorCode", payload.errorCode())
                .addValue("traceId", payload.traceId())
                .addValue("ip", payload.ip());

        int inserted = namedJdbc.update(
                """
                INSERT INTO audit.audit_event (
                    id, occurred_at, actor_id, actor_username, actor_roles, actor_type,
                    module, action, target_type, target_id, target_label,
                    before, after, reason, outcome, error_code, trace_id, ip
                )
                SELECT
                    :id, :occurredAt, :actorId, :actorUsername, :actorRoles, :actorType,
                    :module, :action, :targetType, :targetId, :targetLabel,
                    CAST(:before AS jsonb), CAST(:after AS jsonb), :reason, :outcome, :errorCode, :traceId, :ip
                WHERE NOT EXISTS (SELECT 1 FROM audit.audit_event WHERE id = :id)
                  AND NOT EXISTS (SELECT 1 FROM audit.audit_event_archive WHERE id = :id)
                ON CONFLICT (id, occurred_at) DO NOTHING
                """,
                params);
        return inserted > 0;
    }

    public Page<AuditEventView> findEvents(
            AuditQueryCriteria criteria,
            boolean includeArchive,
            Pageable pageable) {
        String source = includeArchive
                ? """
                  (
                      SELECT id, occurred_at, actor_id, actor_username, actor_roles, actor_type,
                             module, action, target_type, target_id, target_label,
                             before, after, reason, outcome, error_code, trace_id, ip
                      FROM audit.audit_event
                      UNION ALL
                      SELECT id, occurred_at, actor_id, actor_username, actor_roles, actor_type,
                             module, action, target_type, target_id, target_label,
                             before, after, reason, outcome, error_code, trace_id, ip
                      FROM audit.audit_event_archive
                  ) AS audit_rows
                  """
                : "audit.audit_event AS audit_rows";

        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(criteria, params);

        Long totalCount = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM " + source + whereClause,
                params,
                Long.class);
        long total = totalCount == null ? 0L : totalCount;
        if (total == 0L) {
            return new PageImpl<>(List.of(), pageable, 0L);
        }

        String orderBy = buildOrderBy(pageable.getSort());
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        String querySql = """
                SELECT id, occurred_at, actor_id, actor_username, actor_roles, actor_type,
                       module, action, target_type, target_id, target_label,
                       before, after, reason, outcome, error_code, trace_id, ip
                FROM %s%s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(source, whereClause, orderBy);

        List<AuditEventView> rows = namedJdbc.query(querySql, params, rowMapper());
        return new PageImpl<>(rows, pageable, total);
    }

    private String buildWhereClause(AuditQueryCriteria criteria, MapSqlParameterSource params) {
        if (criteria == null) {
            return "";
        }
        List<String> predicates = new ArrayList<>();
        if (criteria.targetType() != null && !criteria.targetType().isBlank()) {
            predicates.add("target_type = :targetType");
            params.addValue("targetType", criteria.targetType().trim());
        }
        if (criteria.targetId() != null && !criteria.targetId().isBlank()) {
            predicates.add("target_id = :targetId");
            params.addValue("targetId", criteria.targetId().trim());
        }
        if (criteria.actorId() != null) {
            predicates.add("actor_id = :actorId");
            params.addValue("actorId", criteria.actorId());
        }
        if (criteria.action() != null && !criteria.action().isBlank()) {
            predicates.add("action = :action");
            params.addValue("action", criteria.action().trim());
        }
        if (criteria.from() != null) {
            predicates.add("occurred_at >= :from");
            params.addValue("from", Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            predicates.add("occurred_at <= :to");
            params.addValue("to", Timestamp.from(criteria.to()));
        }
        if (predicates.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", predicates);
    }

    private static String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "occurred_at DESC, id DESC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new IllegalArgumentException("Unsupported audit sort property: " + order.getProperty());
            }
            clauses.add(column + (order.isAscending() ? " ASC" : " DESC"));
        }
        clauses.add("id DESC");
        return String.join(", ", clauses);
    }

    private RowMapper<AuditEventView> rowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private AuditEventView mapRow(ResultSet rs) throws SQLException {
        OffsetDateTime occurredAt = rs.getObject("occurred_at", OffsetDateTime.class);
        return new AuditEventView(
                rs.getObject("id", UUID.class),
                occurredAt == null ? null : occurredAt.toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_username"),
                readTextArray(rs.getArray("actor_roles")),
                AuditActorType.fromCode(rs.getInt("actor_type")),
                rs.getString("module"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("target_label"),
                fromJson(rs.getString("before")),
                fromJson(rs.getString("after")),
                rs.getString("reason"),
                AuditOutcome.fromCode(rs.getInt("outcome")),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                rs.getString("ip"));
    }

    private static List<String> readTextArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object raw = sqlArray.getArray();
            if (raw instanceof String[] strings) {
                return List.of(strings);
            }
            if (raw instanceof Object[] objects) {
                List<String> list = new ArrayList<>(objects.length);
                for (Object item : objects) {
                    if (item != null) {
                        list.add(String.valueOf(item));
                    }
                }
                return List.copyOf(list);
            }
            return List.of();
        } finally {
            sqlArray.free();
        }
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize audit payload JSON", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize audit JSON column", ex);
        }
    }
}
