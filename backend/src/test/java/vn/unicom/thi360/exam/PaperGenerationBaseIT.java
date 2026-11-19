package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Shared helpers of the generation port tests (EX-2): versions are published through the real check and publish
 * endpoints, either from a 2B template or from a small structure over a private subject with a controlled pool.
 */
@Transactional
abstract class PaperGenerationBaseIT extends AbstractIntegrationTest {
    protected static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    protected static final UUID HSA_KIND = UUID.fromString("00000000-0000-7000-8200-000000000101");
    protected static final UUID QDA_KIND = UUID.fromString("00000000-0000-7000-8200-000000000102");
    private static final String SC4 = "{\"type\":\"SINGLE_CHOICE\",\"options\":["
            + "{\"id\":\"a\",\"correct\":true},{\"id\":\"b\"},{\"id\":\"c\"},{\"id\":\"d\"}]}";

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected EntityManager entities;
    @Autowired protected PaperSolverPort port;

    protected record Published(UUID examId, int versionNo) {}

    protected record Subject(UUID id, String code) {}

    /** Part ids of the published version, in structure order (template parts carry generated ids). */
    protected List<String> partIds(Published published) throws Exception {
        JsonNode parts = mapper.readTree(jdbc.queryForObject("SELECT parts::text FROM exam.exam_version "
                + "WHERE exam_id = ? AND version_no = ?", String.class, published.examId(), published.versionNo()));
        List<String> ids = new ArrayList<>();
        parts.path("parts").forEach(part -> ids.add(part.path("id").asText()));
        return ids;
    }
    // ---- publishing -----------------------------------------------------------------------------------------

    /** Create an exam from the default 2B template, check it and publish every valid path. */
    protected Published publishTemplate(String kind, String scope, String partCode, String subjectCode,
            String buildMode) throws Exception {
        var request = mapper.createObjectNode().put("kind", kind).put("scope", scope)
                .put("name", "EX-2 " + kind + " " + scope)
                .put("code", "EX2-" + UUID.randomUUID().toString().substring(0, 8));
        if (buildMode != null) request.put("buildMode", buildMode);
        if (partCode != null) request.put("partCode", partCode);
        if (subjectCode != null) request.put("subjectCode", subjectCode);
        request.put("durationMinutes", 60);
        JsonNode exam = json(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()), 201);
        return checkAndPublish(UUID.fromString(exam.path("id").asText()));
    }

    /**
     * Insert an exam with the given structure over the standard catalog, check it and publish every valid path.
     * Block references are derived from the structure so the check can run.
     */
    protected Published publishStructure(UUID kindItemId, String partsJson) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,kind_item_id,scope,timing,question_count,"
                + "has_unpublished_changes,duration_minutes) VALUES (?,?,?,'EX-2 structure',?,'FULL','SHARED',"
                + "(SELECT sum((p->>'questionCount')::integer) FROM jsonb_array_elements(?::jsonb->'parts') p),true,60)",
                id, id.toString(), id.toString(), kindItemId, partsJson);
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'ex2')",
                id, partsJson);
        insertBlockRefs(id, mapper.readTree(partsJson));
        return checkAndPublish(id);
    }

    private void insertBlockRefs(UUID examId, JsonNode structure) {
        for (JsonNode part : structure.path("parts")) {
            String partId = part.path("id").asText();
            refs(examId, partId, part.path("sections"));
            refs(examId, partId, part.path("subjectPool"));
            for (JsonNode option : part.path("options")) refs(examId, partId, option.path("sections"));
        }
    }

    private void refs(UUID examId, String partId, JsonNode sections) {
        for (JsonNode section : sections) {
            UUID subject = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE group_id = ? AND code = ?",
                    UUID.class, SUBJECT_GROUP, section.path("subjectCode").asText());
            for (JsonNode block : section.path("blocks")) {
                jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) "
                        + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), examId, block.path("id").asText(), partId,
                        section.path("id").asText(), subject);
            }
        }
    }

    /**
     * Insert a published version row directly (no check, no ledger): enough for the port, which reads only
     * {@code exam_version}. {@code combinations} is the JSON array of {@code config.combinations}, or null.
     */
    protected Published insertVersion(String partsJson, String combinations) {
        UUID examId = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,duration_minutes) "
                + "VALUES (?,?,?,'EX2 direct','FULL','SHARED',60)", examId, examId.toString(), examId.toString());
        String config = "{\"code\":\"" + examId + "\",\"name\":\"EX2 direct\",\"scope\":\"FULL\",\"timing\":\"SHARED\","
                + "\"durationMinutes\":60,\"practiceScoring\":\"RAW\""
                + (combinations == null ? "" : ",\"combinations\":" + combinations) + "}";
        jdbc.update("INSERT INTO exam.exam_version(id,exam_id,version_no,config,parts,published_path_ids,"
                + "serving_path_ids,check_run_id,state,status,published_at,published_by) "
                + "VALUES (?,?,1,?::jsonb,?::jsonb,ARRAY['default'],ARRAY['default'],?,'SERVING','PUBLISHED',now(),?)",
                UUID.randomUUID(), examId, config, partsJson, UUID.randomUUID(), UUID.randomUUID());
        return new Published(examId, 1);
    }

    /** One part {@code p1}, one section {@code s1} of the subject, one single-choice group {@code b1}. */
    protected static String singleSection(Subject subject, int count) {
        return "{\"parts\":[{\"id\":\"p1\",\"order\":1,\"name\":\"P1\",\"questionCount\":" + count
                + ",\"questionCountOnPaper\":" + count + ",\"questionCountRequired\":" + count
                + ",\"participation\":{\"mode\":\"ALL\",\"choose\":null},\"sections\":[{\"id\":\"s1\",\"subjectCode\":\""
                + subject.code() + "\",\"questionCount\":" + count
                + ",\"blocks\":[{\"id\":\"b1\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":" + count
                + "}]}],\"options\":[]}]}";
    }

    protected Published checkAndPublish(UUID examId) throws Exception {
        String base = "/api/v1/admin/exams/" + examId;
        JsonNode checked = json(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
        var paths = mapper.createArrayNode();
        checked.path("paths").forEach(path -> {
            if ("VALID".equals(path.path("result").asText())) paths.add(path.path("pathId").asText());
        });
        assertThat(paths).as("valid check paths: %s", checked).isNotEmpty();
        entities.clear();
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, examId);
        var request = mapper.createObjectNode().put("expectedRevision", revision);
        request.set("pathIds", paths);
        json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()), 200);
        entities.clear();
        int version = jdbc.queryForObject("SELECT max(version_no) FROM exam.exam_version WHERE exam_id = ?",
                Integer.class, examId);
        return new Published(examId, version);
    }

    protected JsonNode json(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    protected JsonNode versionConfig(Published published) throws Exception {
        return mapper.readTree(jdbc.queryForObject("SELECT config::text FROM exam.exam_version "
                + "WHERE exam_id = ? AND version_no = ?", String.class, published.examId(), published.versionNo()));
    }

    // ---- private subjects and questions ---------------------------------------------------------------------

    protected Subject newSubject() {
        UUID id = UUID.randomUUID();
        String code = "EX2_" + id.toString().replace("-", "").substring(0, 16);
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,name_en,"
                + "display_order,status,is_system,depth,path) VALUES (?,?,?,?,(SELECT coalesce(max(value),0)+1 "
                + "FROM catalog.catalog_item WHERE group_id=?),'EX2 subject','EX2 subject',0,1,false,1,?)",
                id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    protected List<UUID> singles(Subject subject, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                    + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,1)", id, "EX2-" + id, subject.id());
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) "
                    + "VALUES (?,1,?::jsonb)", id, "{\"content\":\"q\",\"points\":1,\"body\":" + SC4 + "}");
            ids.add(id);
        }
        return ids;
    }

    /** Question sets of {@code size} single-choice children each. */
    protected List<UUID> sets(Subject subject, int count, int size) {
        List<UUID> roots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID root = UUID.randomUUID();
            var body = new StringBuilder("{\"type\":\"QUESTION_SET\",\"passage\":\"p\",\"children\":[");
            List<UUID> children = new ArrayList<>();
            for (int position = 0; position < size; position++) {
                UUID child = UUID.randomUUID();
                children.add(child);
                body.append(position == 0 ? "" : ",").append("{\"id\":\"").append(child).append("\",\"body\":")
                        .append(SC4).append('}');
            }
            body.append("]}");
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,"
                    + "points) VALUES (?,?,'QUESTION_SET','READY',?,1,1)", root, "EX2S-" + root, subject.id());
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) "
                    + "VALUES (?,1,?::jsonb)", root, "{\"content\":\"s\",\"points\":1,\"body\":" + body + "}");
            for (int position = 0; position < size; position++) {
                UUID child = children.get(position);
                jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) "
                        + "VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "EX2C-" + child, root, position);
                jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) "
                        + "VALUES (?,1,?::jsonb)", child, "{\"content\":\"c\",\"points\":1,\"body\":" + SC4 + "}");
            }
            roots.add(root);
        }
        return roots;
    }

    /**
     * {@code copies} copies of each source question (row, versions, knowledge, tags; a cluster brings its children
     * with new ids): more source of exactly the same shape, so every filter that matched the original matches the copy.
     */
    protected void cloneMany(java.util.Collection<UUID> sources, int copies) {
        if (sources.isEmpty() || copies < 1) return;
        String ids = sources.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",", "'{", "}'"));
        jdbc.execute("DROP TABLE IF EXISTS ex2_roots");
        jdbc.execute("DROP TABLE IF EXISTS ex2_kids");
        jdbc.execute("CREATE TEMP TABLE ex2_roots AS SELECT q.id AS old_id, gen_random_uuid() AS new_id "
                + "FROM questionbank.question q CROSS JOIN generate_series(1, " + copies + ") g "
                + "WHERE q.id = ANY(" + ids + "::uuid[])");
        jdbc.execute("CREATE TEMP TABLE ex2_kids AS SELECT r.new_id AS root_new, c.id AS old_id, "
                + "gen_random_uuid() AS new_id FROM ex2_roots r JOIN questionbank.question c ON c.parent_id = r.old_id");
        String row = "INSERT INTO questionbank.question SELECT (jsonb_populate_record(NULL::questionbank.question, "
                + "to_jsonb(q) || jsonb_build_object('id', m.new_id, 'code', 'EX2K-' || m.new_id, "
                + "'source_code', null::text, 'source_code_normalized', null::text%s))).* "
                + "FROM questionbank.question q JOIN %s m ON m.old_id = q.id";
        jdbc.execute(String.format(row, "", "ex2_roots"));
        jdbc.execute(String.format(row, ", 'parent_id', m.root_new", "ex2_kids"));
        String both = "(SELECT old_id, new_id FROM ex2_roots UNION ALL SELECT old_id, new_id FROM ex2_kids)";
        jdbc.execute("INSERT INTO questionbank.question_version(question_id,version_no,snapshot,saved_by,saved_at,ready_at) "
                + "SELECT m.new_id, v.version_no, v.snapshot, v.saved_by, v.saved_at, v.ready_at "
                + "FROM questionbank.question_version v JOIN " + both + " m ON m.old_id = v.question_id");
        jdbc.execute("DO $$ DECLARE k record; BEGIN FOR k IN SELECT * FROM ex2_kids LOOP "
                + "UPDATE questionbank.question_version SET snapshot = replace(snapshot::text, k.old_id::text, "
                + "k.new_id::text)::jsonb WHERE question_id = k.root_new; END LOOP; END $$");
        jdbc.execute("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) "
                + "SELECT m.new_id, k.knowledge_item_id, k.is_primary FROM questionbank.question_knowledge k "
                + "JOIN " + both + " m ON m.old_id = k.question_id");
        jdbc.execute("INSERT INTO questionbank.question_tag(question_id,tag_item_id) "
                + "SELECT m.new_id, t.tag_item_id FROM questionbank.question_tag t "
                + "JOIN " + both + " m ON m.old_id = t.question_id");
        jdbc.execute("DROP TABLE ex2_roots");
        jdbc.execute("DROP TABLE ex2_kids");
    }

    // ---- reading results ------------------------------------------------------------------------------------

    /** Root question ids of the paper, in port order. */
    protected static List<UUID> roots(GeneratedPaper paper) {
        List<UUID> ids = new ArrayList<>();
        for (GeneratedPart part : paper.parts()) {
            part.sections().forEach(section -> section.groups().forEach(group ->
                    group.questions().forEach(question -> ids.add(question.questionId()))));
        }
        return ids;
    }

    protected static Set<UUID> rootSet(GeneratedPaper paper) {
        return new LinkedHashSet<>(roots(paper));
    }

    protected static List<UUID> roots(GeneratedSection section) {
        List<UUID> ids = new ArrayList<>();
        section.groups().forEach(group -> group.questions().forEach(question -> ids.add(question.questionId())));
        return ids;
    }

    protected static GeneratedPart part(GeneratedPaper paper, String partCode) {
        return paper.parts().stream().filter(part -> partCode.equals(part.partCode())).findFirst().orElseThrow();
    }

    protected static GeneratedSection section(GeneratedPaper paper, String subjectCode) {
        return paper.parts().stream().flatMap(part -> part.sections().stream())
                .filter(section -> subjectCode.equals(section.subjectCode())).findFirst().orElseThrow();
    }

    /** Counting units (children of a set, or one) of a root question. */
    protected int units(UUID root) {
        Integer children = jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE parent_id = ?",
                Integer.class, root);
        return children == 0 ? 1 : children;
    }

    protected static List<UUID> versionedRoots(GeneratedSection section) {
        return roots(section);
    }

    protected static GeneratedQuestion question(UUID id) {
        return new GeneratedQuestion(id, 1);
    }

    protected MockHttpServletRequestBuilder getVersion(Published published) {
        return get("/api/v1/admin/exams/" + published.examId() + "/versions/" + published.versionNo())
                .with(asUser(Permissions.EXAM_VIEW));
    }
}
