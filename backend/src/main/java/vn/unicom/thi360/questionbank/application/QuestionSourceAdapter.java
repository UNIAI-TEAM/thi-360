package vn.unicom.thi360.questionbank.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.questionbank.spi.AnswerFormat;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Reads only the serving projection of READY root questions. */
@Service
public class QuestionSourceAdapter implements QuestionSourcePort {
    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogLookup catalog;
    private final QuestionSupportDao support;
    private final ObjectMapper mapper;

    public QuestionSourceAdapter(NamedParameterJdbcTemplate jdbc, CatalogLookup catalog,
            QuestionSupportDao support, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.support = support;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Description> describe(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();
        String sql = """
                select q.id, q.parent_id, root.subject_item_id, root.type,
                       array(select distinct x.type from questionbank.question x
                             where ((root.type <> 'QUESTION_SET' and x.id = root.id)
                                 or (root.type = 'QUESTION_SET' and x.parent_id = root.id))
                                 and x.deleted_at is null) answer_types,
                       array(select distinct k.knowledge_item_id from questionbank.question_knowledge k
                             join questionbank.question x on x.id = k.question_id
                             where (x.id = root.id or x.parent_id = root.id) and x.deleted_at is null) knowledge_ids
                from questionbank.question q
                join questionbank.question root on root.id = coalesce(q.parent_id, q.id)
                join questionbank.question_version v on v.question_id = root.id
                    and v.version_no = root.current_version_no
                where q.id in (:ids) and q.deleted_at is null and root.deleted_at is null
                    and root.parent_id is null and root.status = 'READY'
                """;
        Map<UUID, Description> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("ids", questionIds), rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String[] answerTypes = (String[]) rs.getArray("answer_types").getArray();
            UUID[] knowledgeIds = (UUID[]) rs.getArray("knowledge_ids").getArray();
            result.put(id, new Description(id, rs.getObject("parent_id", UUID.class),
                    rs.getObject("subject_item_id", UUID.class), QuestionType.valueOf(rs.getString("type")),
                    java.util.Arrays.stream(answerTypes).map(QuestionType::valueOf).toList(),
                    List.of(knowledgeIds)));
        });
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Counts count(Filter filter) {
        return countUnion(List.of(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public Counts countUnion(List<Filter> filters) {
        if (filters.isEmpty()) return new Counts(0, 0, List.of(), List.of(), List.of());
        var params = new MapSqlParameterSource();
        String sql = roots(filters, params, null) + units() + """
                select answer_type, grade_id, level_id, competency_id,
                       count(*) as n, coalesce(sum(command_count), 0) as command_count
                from units group by answer_type, grade_id, level_id, competency_id
                """;
        var cells = new LinkedHashMap<List<String>, Long>();
        var answers = new LinkedHashMap<QuestionType, Long>();
        var competencies = new LinkedHashMap<String, Long>();
        Map<UUID, String> codes = catalogCodes();
        long[] total = {0};
        long[] commandTotal = {0};
        jdbc.query(sql, params, rs -> {
            long n = rs.getLong("n");
            total[0] += n;
            commandTotal[0] += rs.getLong("command_count");
            String grade = codes.get(rs.getObject("grade_id", UUID.class));
            String level = codes.get(rs.getObject("level_id", UUID.class));
            var key = java.util.Arrays.asList(grade, level);
            cells.merge(key, n, Long::sum);
            answers.merge(QuestionType.valueOf(rs.getString("answer_type")), n, Long::sum);
            String competency = codes.get(rs.getObject("competency_id", UUID.class));
            if (competency != null) competencies.merge(competency, n, Long::sum);
        });
        return new Counts(total[0], commandTotal[0], cells.entrySet().stream()
                .map(e -> new CellCount(e.getKey().get(0), e.getKey().get(1), e.getValue())).toList(),
                answers.entrySet().stream().map(e -> new AnswerTypeCount(e.getKey(), e.getValue())).toList(),
                competencies.entrySet().stream().map(e -> new CompetencyCount(e.getKey(), e.getValue())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> findCandidates(Filter filter, int limit) {
        if (limit <= 0) return List.of();
        var params = new MapSqlParameterSource();
        String sql = roots(List.of(filter), params, limit) + units() + """
                select units.*, r.task_type_item_id, r.code root_code, unit_question.code unit_code,
                       coalesce(r.snapshot #>> '{body,scoringRule}', r.snapshot #>> '{body,scoringMethod}') scoring_rule,
                       r.snapshot #>> '{body,scoringMethod}' root_tf_method,
                       (r.snapshot #> '{body,scoreTable}')::text root_tf_table,
                       r.snapshot #>> '{body,scoringRule}' root_mc_rule,
                       (case when units.unit_id = r.id then r.snapshot else child.snapshot end) #>> '{body,scoringMethod}' unit_tf_method,
                       ((case when units.unit_id = r.id then r.snapshot else child.snapshot end) #> '{body,scoreTable}')::text unit_tf_table,
                       (case when units.unit_id = r.id then r.snapshot else child.snapshot end) #>> '{body,scoringRule}' unit_mc_rule,
                       array(select knowledge_item_id from questionbank.question_knowledge k
                             where k.question_id = units.unit_id order by knowledge_item_id) mapids
                from units join roots r on r.id = units.root_id
                join questionbank.question unit_question on unit_question.id = units.unit_id
                left join questionbank.question_version child
                  on child.question_id = units.unit_id and child.version_no = r.current_version_no
                     and units.unit_id <> r.id
                order by units.root_id, units.unit_order
                """;
        Map<UUID, String> codes = catalogCodes();
        Map<UUID, List<Unit>> grouped = new LinkedHashMap<>();
        Map<UUID, Candidate> candidates = new LinkedHashMap<>();
        Map<UUID, ScoringDetails> scoringByUnit = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            UUID root = rs.getObject("root_id", UUID.class);
            UUID unitId = rs.getObject("unit_id", UUID.class);
            Array mapids = rs.getArray("mapids");
            List<UUID> ids = mapids == null ? List.of() : List.of((UUID[]) mapids.getArray());
            UUID competencyId = rs.getObject("competency_id", UUID.class);
            String competency = codes.get(competencyId);
            if (!scoringByUnit.containsKey(unitId)) {
                scoringByUnit.put(unitId, scoring(rs.getString("unit_tf_method"), rs.getString("unit_tf_table"),
                        rs.getString("unit_mc_rule")));
            }
            String answerFormat = rs.getString("answer_format");
            Unit unit = new Unit(unitId, rs.getString("reference"),
                    QuestionType.valueOf(rs.getString("answer_type")),
                    codes.get(rs.getObject("grade_id", UUID.class)),
                    codes.get(rs.getObject("level_id", UUID.class)), ids,
                    competency == null ? List.of() : List.of(competency), rs.getBigDecimal("points"),
                    scoringByUnit.get(unitId), rs.getString("unit_code"), rs.getInt("command_count"),
                    (Integer) rs.getObject("statement_count"), (Integer) rs.getObject("left_count"),
                    (Integer) rs.getObject("right_count"),
                    answerFormat == null ? null : AnswerFormat.valueOf(answerFormat),
                    rs.getBoolean("has_attachment"));
            grouped.computeIfAbsent(root, ignored -> new ArrayList<>()).add(unit);
            if (!candidates.containsKey(root)) {
                String type = rs.getString("root_type");
                ScoringDetails rootScoring = root.equals(unitId) ? scoringByUnit.get(unitId)
                        : scoring(rs.getString("root_tf_method"), rs.getString("root_tf_table"), rs.getString("root_mc_rule"));
                candidates.put(root, new Candidate(root, rs.getInt("version_no"),
                        type.equals("QUESTION_SET") || type.equals("CLOZE") ? root : null,
                        grouped.get(root), codes.get(rs.getObject("task_type_item_id", UUID.class)),
                        rs.getString("scoring_rule"), rootScoring == null ? null : rootScoring.trueFalseMethod(),
                        rootScoring == null ? null : rootScoring.trueFalseTable(),
                        rootScoring == null ? null : rootScoring.multipleChoiceRule(), rs.getString("root_code")));
            }
        });
        return candidates.values().stream().map(c -> new Candidate(c.questionId(), c.versionNo(), c.setId(),
                List.copyOf(c.units()), c.taskTypeCode(), c.scoringRule(), c.trueFalseMethod(),
                c.trueFalseTable(), c.multipleChoiceRule(), c.code())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> matching(Filter filter, Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) return Set.of();
        var params = new MapSqlParameterSource("questionIds", questionIds);
        String sql = roots(List.of(filter), params, null,
                " and q.id in (select coalesce(parent_id, id) from questionbank.question where id in (:questionIds))")
                + units()
                + """
                select distinct requested.id
                from questionbank.question requested
                join roots r on r.id = coalesce(requested.parent_id, requested.id)
                where requested.id in (:questionIds) and requested.deleted_at is null
                  and exists (select 1 from units u where u.root_id = r.id)
                  and (requested.id = r.id or
                       (r.type = 'QUESTION_SET' and exists
                           (select 1 from units u where u.root_id = r.id and u.unit_id = requested.id)))
                """;
        return Set.copyOf(jdbc.queryForList(sql, params, UUID.class));
    }

    private ScoringDetails scoring(String method, String tableJson, String multipleChoiceRule) throws SQLException {
        List<BigDecimal> table = null;
        if (tableJson != null) {
            try {
                JsonNode node = mapper.readTree(tableJson);
                if (!node.isNull()) {
                    if (!node.isArray()) throw new SQLException("Invalid serving scoring table");
                    table = new ArrayList<>(node.size());
                    for (JsonNode value : node) {
                        if (!value.isNumber()) throw new SQLException("Invalid serving scoring table value");
                        table.add(value.decimalValue());
                    }
                }
            } catch (JsonProcessingException e) {
                throw new SQLException("Invalid serving scoring table", e);
            }
        }
        return method == null && table == null && multipleChoiceRule == null
                ? null : new ScoringDetails(method, table, multipleChoiceRule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Version> loadVersions(List<VersionKey> versions) {
        if (versions.isEmpty()) return List.of();
        var params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            VersionKey key = versions.get(i);
            clauses.add("(v.question_id = :id" + i + " and v.version_no = :v" + i + ")");
            params.addValue("id" + i, key.questionId()).addValue("v" + i, key.versionNo());
        }
        return jdbc.query("""
                select v.question_id, v.version_no, v.snapshot::text snapshot, q.code,
                       coalesce((select jsonb_object_agg(c.id::text, c.code)
                                 from questionbank.question c where c.parent_id = q.id),
                                '{}'::jsonb)::text child_codes
                from questionbank.question_version v
                join questionbank.question q on q.id = v.question_id
                where
                """ + String.join(" or ", clauses), params, (rs, n) -> {
                    try {
                        Map<UUID, String> children = new HashMap<>();
                        mapper.readTree(rs.getString("child_codes")).fields().forEachRemaining(entry ->
                                children.put(UUID.fromString(entry.getKey()), entry.getValue().asText()));
                        return new Version(new VersionKey(rs.getObject("question_id", UUID.class),
                                rs.getInt("version_no")), mapper.readTree(rs.getString("snapshot")),
                                rs.getString("code"), children);
                    } catch (JsonProcessingException e) {
                        throw new SQLException("Invalid question snapshot", e);
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> sourceRevisions(Collection<UUID> subjectIds) {
        return support.sourceRevisions(subjectIds);
    }

    @Override
    @Transactional
    public void recordUsage(UUID examId, QuestionUsageKind kind, List<VersionKey> versions) {
        if (versions.isEmpty()) return;
        List<UUID> ids = versions.stream().map(VersionKey::questionId).distinct().sorted().toList();
        var params = new MapSqlParameterSource("ids", ids);
        List<UUID> locked = jdbc.queryForList("select id from questionbank.question where id in (:ids) and deleted_at is null order by id for update", params, UUID.class);
        if (locked.size() != ids.size()) throw new IllegalStateException("Question no longer exists");
        for (VersionKey version : versions.stream().distinct().toList()) {
            Integer exists = jdbc.queryForObject(
                    "select count(*) from questionbank.question_version where question_id=:id and version_no=:version",
                    new MapSqlParameterSource("id", version.questionId()).addValue("version", version.versionNo()),
                    Integer.class);
            if (exists == null || exists == 0) throw new IllegalStateException("Question version no longer exists");
            jdbc.update("""
                    insert into questionbank.question_usage_ledger(question_id, version_no, kind, exam_id)
                    values (:id, :version, :kind, :exam)
                    on conflict do nothing
                    """, new MapSqlParameterSource("id", version.questionId()).addValue("version", version.versionNo())
                    .addValue("kind", kind.name()).addValue("exam", examId));
        }
    }

    private Map<UUID, String> catalogCodes() {
        Map<UUID, String> result = new HashMap<>();
        for (String group : List.of("GRADE", "QUESTION_LEVEL", "COMPETENCY", "TASK_TYPE")) {
            for (CatalogItemView item : catalog.listGroupItems(group)) result.put(item.id(), item.code());
        }
        return result;
    }

    private String roots(List<Filter> filters, MapSqlParameterSource params, Integer limit) {
        return roots(filters, params, limit, "");
    }

    private String roots(List<Filter> filters, MapSqlParameterSource params, Integer limit, String restriction) {
        List<String> alternatives = new ArrayList<>();
        for (int i = 0; i < filters.size(); i++) alternatives.add(predicate(filters.get(i), i, params));
        String tail = "";
        if (limit != null) { params.addValue("limit", limit); tail = " limit :limit"; }
        return "with roots as (select q.*, v.snapshot from questionbank.question q "
                + "join questionbank.question_version v on v.question_id=q.id and v.version_no=q.current_version_no "
                + "where q.parent_id is null and q.deleted_at is null and q.status='READY' and ("
                + String.join(" or ", alternatives) + ")" + restriction + " order by q.id" + tail + "), ";
    }

    private void addCompetencyGroup(List<String> parts, MapSqlParameterSource params,
            String parameter, List<String> codes) {
        if (codes == null || codes.isEmpty()) return;
        List<UUID> ids = codes.stream().map(c -> catalog.findActiveItem("COMPETENCY", c))
                .flatMap(java.util.Optional::stream).map(CatalogItemView::id).toList();
        if (ids.isEmpty()) parts.add("false");
        else {
            params.addValue(parameter, ids);
            parts.add("q.competency_item_id in (:" + parameter + ")");
        }
    }

    private String predicate(Filter f, int index, MapSqlParameterSource params) {
        String p = "f" + index + "_";
        List<String> parts = new ArrayList<>();
        params.addValue(p + "subject", f.subjectId());
        parts.add("q.subject_item_id = :" + p + "subject");
        if (f.questionType() != null) {
            params.addValue(p + "type", f.questionType().name());
            parts.add("q.type = :" + p + "type");
        } else {
            parts.add("q.type not in ('QUESTION_SET','CLOZE')");
            if (f.answerTypes() != null && !f.answerTypes().isEmpty()) {
                params.addValue(p + "answers", f.answerTypes().stream().map(Enum::name).toList());
                parts.add("q.type in (:" + p + "answers)");
            }
        }
        if (f.childAnswerType() != null) {
            params.addValue(p + "child", f.childAnswerType().name());
            parts.add("(q.type <> 'QUESTION_SET' or not exists (select 1 from questionbank.question c "
                    + "where c.parent_id=q.id and c.deleted_at is null and c.type <> :" + p + "child))");
        }
        if (f.taskTypeCode() != null) {
            var item = catalog.findActiveItem("TASK_TYPE", f.taskTypeCode());
            if (item.isEmpty()) parts.add("false");
            else {
                params.addValue(p + "task", item.get().id());
                parts.add("q.task_type_item_id = :" + p + "task");
            }
        }
        addCompetencyGroup(parts, params, p + "competencies", f.competencyCodes());
        if (f.additionalCompetencyGroups() != null) {
            for (int j = 0; j < f.additionalCompetencyGroups().size(); j++) {
                addCompetencyGroup(parts, params, p + "extraCompetencies" + j,
                        f.additionalCompetencyGroups().get(j));
            }
        }
        addTagGroup(parts, params, p + "tags", f.tagCodes());
        if (f.additionalTagGroups() != null) {
            for (int j = 0; j < f.additionalTagGroups().size(); j++) {
                addTagGroup(parts, params, p + "extraTags" + j, f.additionalTagGroups().get(j));
            }
        }
        addKnowledgeGroup(parts, params, p + "knowledge", f.knowledgeGroups());
        addEveryChildKnowledge(parts, params, p + "everyChildKnowledge", f.knowledgeGroups(),
                Boolean.TRUE.equals(f.everyChildInScope()));
        addEveryChildSameNode(parts, params, p + "sameNodeKnowledge", f.knowledgeGroups(),
                Boolean.TRUE.equals(f.everyChildSameNode()));
        if (f.additionalKnowledgeGroups() != null) {
            for (int j = 0; j < f.additionalKnowledgeGroups().size(); j++) {
                addKnowledgeGroup(parts, params, p + "extraKnowledge" + j + "_",
                        f.additionalKnowledgeGroups().get(j));
                boolean everyChild = f.additionalEveryChildInScope() != null
                        && j < f.additionalEveryChildInScope().size()
                        && Boolean.TRUE.equals(f.additionalEveryChildInScope().get(j));
                addEveryChildKnowledge(parts, params, p + "extraEveryChildKnowledge" + j,
                        f.additionalKnowledgeGroups().get(j), everyChild);
            }
        }
        addExcludedKnowledge(parts, params, p + "excludedKnowledge", f.excludedKnowledge());
        if (f.itemShape() != null) {
            String rootShape = shapePredicate("q.type", "v.snapshot", p + "shape", f.itemShape(), params);
            String childShape = shapePredicate("c.type", "cv.snapshot", p + "childShape", f.itemShape(), params);
            parts.add("((q.type <> 'QUESTION_SET' and " + rootShape + ") or "
                    + "(q.type = 'QUESTION_SET' and not exists (select 1 "
                    + "from questionbank.question c "
                    + "join questionbank.question_version cv on cv.question_id=c.id "
                    + "and cv.version_no=q.current_version_no "
                    + "where c.parent_id=q.id and c.deleted_at is null and not (" + childShape + "))))");
        }
        if (f.answerFormats() != null && !f.answerFormats().isEmpty()) {
            params.addValue(p + "formats", f.answerFormats().stream().map(Enum::name).toList());
            parts.add("((q.type = 'SHORT_ANSWER' and v.snapshot #>> '{body,answerFormat}' in (:"
                    + p + "formats)) or (q.type = 'QUESTION_SET' and exists (select 1 "
                    + "from questionbank.question c join questionbank.question_version cv "
                    + "on cv.question_id=c.id and cv.version_no=q.current_version_no "
                    + "where c.parent_id=q.id and c.deleted_at is null and c.type='SHORT_ANSWER') "
                    + "and not exists (select 1 from questionbank.question c "
                    + "join questionbank.question_version cv on cv.question_id=c.id "
                    + "and cv.version_no=q.current_version_no where c.parent_id=q.id "
                    + "and c.deleted_at is null and c.type='SHORT_ANSWER' "
                    + "and coalesce(cv.snapshot #>> '{body,answerFormat}', '') not in (:"
                    + p + "formats))))");
        }
        if (f.childConditions() != null && !f.childConditions().isEmpty()) {
            for (int j = 0; j < f.childConditions().size(); j++) {
                parts.add(childConditionPredicate(f.childConditions().get(j), p, j, params));
            }
        }
        if (f.requiredPointsPerQuestion() != null) {
            params.addValue(p + "points", f.requiredPointsPerQuestion());
            parts.add("(case when q.type='QUESTION_SET' then not exists (select 1 "
                    + "from questionbank.question c where c.parent_id=q.id and c.deleted_at is null "
                    + "and c.points <> :" + p + "points) when q.type='CLOZE' then not exists "
                    + "(select 1 from jsonb_array_elements(v.snapshot #> '{body,blanks}') b "
                    + "where (b->>'points')::numeric <> :" + p + "points) else q.points = :" + p + "points end)");
        }
        if (f.audioRequirement() == AudioRequirement.REQUIRED) {
            parts.add(rootHasAudio());
        } else if (f.audioRequirement() == AudioRequirement.FORBIDDEN) {
            parts.add("not " + rootHasAudio());
        }
        return "(" + String.join(" and ", parts) + ")";
    }

    /**
     * Audio lives on the ROOT question only (children carry no files): one of the serving root's
     * attachments has MIME audio/*. {@code Unit.hasAttachment} is true for every file type and is not used.
     */
    private static String rootHasAudio() {
        return "exists (select 1 from jsonb_array_elements_text(case when jsonb_typeof(v.snapshot #> '{attachmentIds}') = 'array' "
                + "then v.snapshot #> '{attachmentIds}' else '[]'::jsonb end) att(id) "
                + "join questionbank.question_asset audio_asset on audio_asset.id = att.id::uuid "
                + "where lower(audio_asset.mime) like 'audio/%')";
    }

    /**
     * Per-position child condition. Knowledge nodes, answer type and item shape are ANDed on the child at
     * each position; a condition that states none of them matches nothing, as before.
     */
    private String childConditionPredicate(ChildCondition condition, String p, int j, MapSqlParameterSource params) {
        boolean nodes = !condition.nodeIds().isEmpty();
        if (!nodes && condition.answerType() == null && condition.itemShape() == null) return "false";
        String from = p + "childFrom" + j;
        String to = p + "childTo" + j;
        params.addValue(from, condition.from());
        params.addValue(to, condition.to());
        StringBuilder source = new StringBuilder("questionbank.question c");
        List<String> where = new ArrayList<>(List.of("c.parent_id=q.id", "c.deleted_at is null", "c.position=pos - 1"));
        if (nodes) {
            String key = p + "childNodes" + j;
            params.addValue(key, condition.nodeIds());
            source.append(" join questionbank.question_knowledge ck on ck.question_id=c.id");
            where.add("ck.knowledge_item_id in (:" + key + ")");
        }
        if (condition.answerType() != null) {
            String key = p + "childType" + j;
            params.addValue(key, condition.answerType().name());
            where.add("c.type = :" + key);
        }
        if (condition.itemShape() != null) {
            source.append(" join questionbank.question_version cv on cv.question_id=c.id "
                    + "and cv.version_no=q.current_version_no");
            where.add(shapePredicate("c.type", "cv.snapshot", p + "childCondShape" + j, condition.itemShape(), params));
        }
        return "(q.type = 'QUESTION_SET' and not exists (select 1 from generate_series(:" + from + ", :" + to
                + ") pos where not exists (select 1 from " + source + " where " + String.join(" and ", where) + ")))";
    }

    /**
     * A set stays only when one knowledge subtree holds a node of EVERY live child (a MAPID SOURCE rule: the
     * children carry the same code, a descendant of it counts). Standalone questions are not affected.
     */
    private static void addEveryChildSameNode(List<String> parts, MapSqlParameterSource params,
            String prefix, List<List<UUID>> groups, boolean enabled) {
        if (!enabled || groups == null || groups.isEmpty()) return;
        List<String> alternatives = new ArrayList<>();
        for (int j = 0; j < groups.size(); j++) {
            if (groups.get(j).isEmpty()) continue;
            String key = prefix + j;
            params.addValue(key, groups.get(j));
            alternatives.add("not exists (select 1 from questionbank.question c "
                    + "where c.parent_id=q.id and c.deleted_at is null and not exists "
                    + "(select 1 from questionbank.question_knowledge ck where ck.question_id=c.id "
                    + "and ck.knowledge_item_id in (:" + key + ")))");
        }
        parts.add(alternatives.isEmpty() ? "false"
                : "(q.type <> 'QUESTION_SET' or (" + String.join(" or ", alternatives) + "))");
    }

    private static void addEveryChildKnowledge(List<String> parts, MapSqlParameterSource params,
            String key, List<List<UUID>> groups, boolean enabled) {
        if (!enabled || groups == null || groups.isEmpty()) return;
        List<UUID> nodes = groups.stream().flatMap(List::stream).distinct().toList();
        if (nodes.isEmpty()) {
            parts.add("false");
        } else {
            params.addValue(key, nodes);
            parts.add("(q.type <> 'QUESTION_SET' or not exists (select 1 from questionbank.question c "
                    + "where c.parent_id=q.id and c.deleted_at is null and not exists "
                    + "(select 1 from questionbank.question_knowledge ck where ck.question_id=c.id "
                    + "and ck.knowledge_item_id in (:" + key + "))))");
        }
    }

    private void addTagGroup(List<String> parts, MapSqlParameterSource params, String key, List<String> codes) {
        if (codes == null || codes.isEmpty()) return;
        List<UUID> ids = codes.stream().map(c -> catalog.findActiveItem("QUESTION_TAG", c))
                .flatMap(java.util.Optional::stream).map(CatalogItemView::id).toList();
        if (ids.isEmpty()) {
            parts.add("false");
        } else {
            params.addValue(key, ids);
            parts.add("exists (select 1 from questionbank.question_tag t "
                    + "where t.question_id=q.id and t.tag_item_id in (:" + key + "))");
        }
    }

    private static void addKnowledgeGroup(List<String> parts, MapSqlParameterSource params, String prefix,
            List<List<UUID>> groups) {
        if (groups == null || groups.isEmpty()) return;
        List<String> alternatives = new ArrayList<>();
        for (int j = 0; j < groups.size(); j++) {
            List<UUID> group = groups.get(j);
            if (group.isEmpty()) {
                alternatives.add("false");
                continue;
            }
            String key = prefix + j;
            params.addValue(key, group);
            alternatives.add("exists (select 1 from questionbank.question_knowledge k "
                    + "join questionbank.question x on x.id=k.question_id "
                    + "where (x.id=q.id or (x.parent_id=q.id and x.deleted_at is null)) "
                    + "and k.knowledge_item_id in (:" + key + "))");
        }
        parts.add("(" + String.join(" or ", alternatives) + ")");
    }

    /**
     * A candidate carrying an excluded node is dropped: the question itself, or for a set any live child (or the
     * set root), exactly the rows the scope filter reads, so a set with one excluded child goes as a whole.
     */
    private static void addExcludedKnowledge(List<String> parts, MapSqlParameterSource params, String key,
            List<UUID> excluded) {
        if (excluded == null || excluded.isEmpty()) return;
        params.addValue(key, excluded);
        parts.add("not exists (select 1 from questionbank.question_knowledge k "
                + "join questionbank.question x on x.id=k.question_id "
                + "where (x.id=q.id or (x.parent_id=q.id and x.deleted_at is null)) "
                + "and k.knowledge_item_id in (:" + key + "))");
    }

    private String shapePredicate(String type, String snapshot, String prefix,
                                  QuestionSourcePort.ItemShape shape, MapSqlParameterSource params) {
        List<String> parts = new ArrayList<>();
        if (shape.statements() != null) {
            params.addValue(prefix + "statements", shape.statements());
            parts.add(type + " = 'TRUE_FALSE' and coalesce(jsonb_array_length("
                    + snapshot + " #> '{body,statements}'), 0) = :" + prefix + "statements");
        }
        if (shape.leftItems() != null) {
            params.addValue(prefix + "left", shape.leftItems());
            parts.add(type + " = 'MATCHING' and coalesce(jsonb_array_length("
                    + snapshot + " #> '{body,left}'), 0) = :" + prefix + "left");
        }
        if (shape.rightItems() != null) {
            params.addValue(prefix + "right", shape.rightItems());
            parts.add(type + " = 'MATCHING' and coalesce(jsonb_array_length("
                    + snapshot + " #> '{body,right}'), 0) = :" + prefix + "right");
        }
        if (shape.options() != null) {
            params.addValue(prefix + "options", shape.options());
            parts.add(type + " in ('SINGLE_CHOICE','MULTIPLE_CHOICE') and coalesce(jsonb_array_length("
                    + snapshot + " #> '{body,options}'), 0) = :" + prefix + "options");
        }
        return parts.isEmpty() ? "true" : String.join(" and ", parts);
    }

    private String units() {
        return """
                raw_units as (
                  select r.id root_id, r.current_version_no version_no, r.type root_type,
                         r.id unit_id, null::text reference, r.type answer_type,
                         r.grade_item_id grade_id, r.level_item_id level_id,
                         r.competency_item_id competency_id, r.points, 0::bigint unit_order,
                         r.snapshot unit_snapshot
                  from roots r where r.type not in ('QUESTION_SET','CLOZE')
                  union all
                  select r.id, r.current_version_no, r.type, c.id, null::text, c.type,
                         coalesce(c.grade_item_id,r.grade_item_id), coalesce(c.level_item_id,r.level_item_id),
                         coalesce(c.competency_item_id,r.competency_item_id), c.points, c.position::bigint,
                         coalesce(cv.snapshot, '{}'::jsonb)
                  from roots r join questionbank.question c on c.parent_id=r.id and c.deleted_at is null
                  left join questionbank.question_version cv
                    on cv.question_id=c.id and cv.version_no=r.current_version_no
                  where r.type='QUESTION_SET'
                  union all
                  select r.id, r.current_version_no, r.type, r.id, b.value->>'id', 'CLOZE',
                         r.grade_item_id, r.level_item_id, r.competency_item_id,
                         (b.value->>'points')::numeric, b.ordinality, r.snapshot
                  from roots r cross join lateral jsonb_array_elements(r.snapshot #> '{body,blanks}') with ordinality b(value, ordinality)
                  where r.type='CLOZE'
                ),
                units as (
                  select raw_units.*,
                         case when answer_type = 'TRUE_FALSE' then coalesce(jsonb_array_length(unit_snapshot #> '{body,statements}'), 0)
                              when answer_type = 'MATCHING' then coalesce(jsonb_array_length(unit_snapshot #> '{body,left}'), 0)
                              else 1 end command_count,
                         case when answer_type = 'TRUE_FALSE' then jsonb_array_length(unit_snapshot #> '{body,statements}') end statement_count,
                         case when answer_type = 'MATCHING' then jsonb_array_length(unit_snapshot #> '{body,left}') end left_count,
                         case when answer_type = 'MATCHING' then jsonb_array_length(unit_snapshot #> '{body,right}') end right_count,
                         case when answer_type = 'SHORT_ANSWER' then unit_snapshot #>> '{body,answerFormat}' end answer_format,
                         coalesce(jsonb_array_length(unit_snapshot #> '{attachmentIds}'), 0) > 0 has_attachment
                  from raw_units
                )
                """;
    }
}
