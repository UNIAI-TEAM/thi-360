package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamVersionLifecycleIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private UUID exam(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, status) values (?, ?, ?, 'Lifecycle', 'FULL', 'SHARED', ?)",
                id, id.toString(), id.toString(), status);
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, '{\"parts\":[]}'::jsonb, 'test')", id);
        return id;
    }

    private void version(UUID id, int number, UUID publisher) {
        jdbc.update("insert into exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by) "
                        + "values (?, ?, ?, '{}'::jsonb, '{\"parts\":[]}'::jsonb, array['default'], array['default'], ?, 'SERVING', 'PUBLISHED', now(), ?)",
                UUID.randomUUID(), id, number, UUID.randomUUID(), publisher);
    }

    @Test
    void discontinueIsAuthorizedRevisionCheckedAuditedAndIdempotent() throws Exception {
        UUID id = exam("PUBLISHED");
        UUID actor = UUID.randomUUID();
        version(id, 1, actor);
        String url = "/api/v1/admin/exams/" + id + "/discontinue";
        String key = UUID.randomUUID().toString();
        String body = "{\"expectedRevision\":0}";
        mockMvc.perform(post(url).header("Origin", "http://localhost:3001").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mockMvc.perform(post(url).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}")).andExpect(status().isPreconditionFailed());
        String first = mockMvc.perform(post(url).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.versions[1].version").value(1))
                .andExpect(jsonPath("$.versions[1].publishedPaths[0].pathId").value("default"))
                .andExpect(jsonPath("$.versions[1].servingPaths[0].pathId").value("default"))
                .andExpect(jsonPath("$.revision").value(1)).andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(post(url).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(replay)).isEqualTo(mapper.readTree(first));
        mockMvc.perform(post(url).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("select status from exam.exam_version where exam_id=?", String.class, id)).isEqualTo("DISCONTINUED");
        assertThat(jdbc.queryForObject("select count(*) from shared.outbox_event where topic='audit' and payload->>'action'='EXAM.DISCONTINUE' and payload->>'targetId'=?", Long.class, id.toString())).isEqualTo(1);
    }

    @Test
    void draftCannotBeDiscontinued() throws Exception {
        UUID id = exam("DRAFT");
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/discontinue")
                .with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_NOT_PUBLISHED"));
    }

    @Test
    void versionsAreNewestFirstAndRequireView() throws Exception {
        UUID id = exam("PUBLISHED");
        UUID actor = UUID.randomUUID();
        version(id, 1, actor);
        version(id, 2, actor);
        String url = "/api/v1/admin/exams/" + id + "/versions";
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_PUBLISH))).andExpect(status().isForbidden());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.examId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].version").value(2)).andExpect(jsonPath("$.items[1].version").value(1));
        mockMvc.perform(get("/api/v1/admin/exams/" + id).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versions[1].version").value(2))
                .andExpect(jsonPath("$.versions[2].version").value(1))
                .andExpect(jsonPath("$.versions[1].publishedPaths[0].pathId").value("default"));
    }

    @Test
    void versionListEnumeratesUnpublishedWorkingPathsInPartOrder() throws Exception {
        UUID id = exam("DRAFT");
        jdbc.update("update exam.exam set has_unpublished_changes=true where id=?", id);
        jdbc.update("""
                update exam.exam_structure set parts=cast(? as jsonb) where exam_id=?
                """, """
                {"parts":[
                  {"id":"later","order":2,"participation":{"mode":"CHOICE","choose":1},
                   "options":[{"id":"b","kind":"SUBJECT","code":"B","sections":[],"blocks":[]}]},
                  {"id":"earlier","order":1,"participation":{"mode":"CHOICE","choose":1},
                   "options":[{"id":"a","kind":"SUBJECT","code":"A","sections":[],"blocks":[]}]}
                ]}
                """, id);
        mockMvc.perform(get("/api/v1/admin/exams/" + id + "/versions").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingCopy.unpublishedPaths[0].pathId").value("a+b"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void unpublishedPathsIgnoreRetiredHistoryAndCountOnlyServingMatchingPaths() throws Exception {
        UUID id = exam("PUBLISHED");
        jdbc.update("update exam.exam set duration_minutes=45,has_unpublished_changes=true,serving_version=2 where id=?", id);
        snapshot(id, 1, "array['default']", "array[]::text[]", "RETIRED", "45");
        snapshot(id, 2, "array['default']", "array['default']", "SERVING", "90");
        mockMvc.perform(get("/api/v1/admin/exams/" + id + "/versions").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingCopy.unpublishedPaths[0].pathId").value("default"));
    }

    @Test
    void matchingVersionsContributeOnlyTheirServingPathsDespiteDifferentResolvedQuotas() throws Exception {
        UUID id = exam("PUBLISHED");
        jdbc.update("update exam.exam set has_unpublished_changes=true,serving_version=2 where id=?", id);
        jdbc.update("""
                update exam.exam_structure set parts='{"parts":[{"id":"p","participation":{"mode":"CHOICE","choose":1},
                "options":[{"id":"a","kind":"SUBJECT","code":"A","sections":[],"blocks":[]},
                {"id":"b","kind":"SUBJECT","code":"B","sections":[],"blocks":[]}]}]}'::jsonb where exam_id=?
                """, id);
        snapshot(id, 1, "array['a','b']", "array['a']", "SERVING", "null");
        snapshot(id, 2, "array['b']", "array['b']", "SERVING", "null");
        jdbc.update("update exam.exam_version set config=jsonb_set(config,'{resolvedQuotas}',jsonb_build_object(version_no::text,version_no)) where exam_id=?", id);
        String url = "/api/v1/admin/exams/" + id + "/versions";
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingCopy.unpublishedPaths").isEmpty());
        jdbc.update("update exam.exam_version set state='RETIRED',serving_path_ids=array[]::text[] where exam_id=? and version_no=2", id);
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingCopy.unpublishedPaths[0].pathId").value("b"));
    }

    @Test
    void reorderingPublishedFixedBlocksMakesTheWorkingPathUnpublished() throws Exception {
        UUID id = exam("PUBLISHED");
        jdbc.update("update exam.exam set has_unpublished_changes=true,serving_version=1 where id=?", id);
        jdbc.update("""
                update exam.exam_structure set parts=cast(? as jsonb) where exam_id=?
                """, """
                {"parts":[{"id":"p","sections":[{"id":"s","groupOrder":"FIXED",
                "blocks":[{"id":"a"},{"id":"b"}]}]}]}
                """, id);
        snapshot(id, 1, "array['default']", "array['default']", "SERVING", "null");
        String url = "/api/v1/admin/exams/" + id + "/versions";
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingCopy.unpublishedPaths").isEmpty());
        jdbc.update("""
                update exam.exam_structure set parts=cast(? as jsonb) where exam_id=?
                """, """
                {"parts":[{"id":"p","sections":[{"id":"s","groupOrder":"FIXED",
                "blocks":[{"id":"b"},{"id":"a"}]}]}]}
                """, id);
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingCopy.unpublishedPaths[0].pathId").value("default"));
    }

    private void snapshot(UUID id, int number, String published, String serving, String state, String duration) {
        jdbc.update("""
                insert into exam.exam_version(id,exam_id,version_no,config,parts,published_path_ids,serving_path_ids,
                    check_run_id,state,status,published_at,published_by)
                select ?,e.id,?,jsonb_build_object('code',e.code,'name',e.name,'description',e.description,
                    'instructions',e.instructions,'kindItemId',e.kind_item_id,'scope',e.scope,'partCode',e.part_code,
                    'subjectItemId',e.subject_item_id,'templateCode',e.template_code,'structureYear',e.structure_year,
                    'matrixYear',e.matrix_year,'yearsConfirmed',e.years_confirmed,'timing',e.timing,
                    'durationMinutes',%s,'maxScore',e.max_score,'referenceMax',e.reference_max,
                    'practiceScoring',e.practice_scoring,'coverAssetId',e.cover_asset_id,
                    'kindProfile',jsonb_build_object('scopes',p.scopes,'templateBy',p.template_by,'timing',p.timing,
                    'maxScore',p.max_score,'referenceMax',p.reference_max,'practiceScoring',p.practice_scoring,
                    'trueFalseScoring',p.tf_scoring,'multipleChoiceRule',p.mc_rule)),
                    s.parts,%s,%s,?,?,'PUBLISHED',now(),?
                from exam.exam e join exam.exam_structure s on s.exam_id=e.id
                left join exam.exam_kind_profile p on p.exam_item_id=e.kind_item_id where e.id=?
                """.formatted(duration, published, serving),
                UUID.randomUUID(), number, UUID.randomUUID(), state, UUID.randomUUID(), id);
    }
}
