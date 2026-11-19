package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

@Transactional
class ExamQuestionUsageIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;
    @Autowired ExamBlockRefProjector projector;
    @Autowired ExamBlockRefRepository refs;
    @Autowired QuestionSourcePort source;
    @Autowired QuestionUsageProvider provider;
    @Autowired ExamRepository exams;
    @Autowired ExamKindProfileRepository profiles;
    @Autowired ExamCheckResultStore checks;
    @Autowired EntityManager entityManager;

    @Test
    void subjectPoolBlocksAreReportedAsQuestionUsages() throws Exception {
        UUID subject = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID exam = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) values (?,?,'SINGLE_CHOICE','READY',?,1,1)",
                question, "POOL-" + question, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", question);
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing,kind_item_id,has_unpublished_changes) values (?,?,?,'Pool exam','FULL','SHARED','00000000-0000-7000-8200-000000000101',true)",
                exam, exam.toString(), exam.toString());
        String json = """
                {"parts":[{"id":"p","name":"Part","codeSource":{"source":"GLOBAL_LIST"},
                  "subjectPool":[{"id":"s","subjectCode":"TEST","blocks":[
                    {"id":"pool-block","questionType":"SINGLE_CHOICE"}]}]}]}
                """;
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        var resolver = new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null);
        refs.saveAllAndFlush(projector.project(exam, document.parts(), resolver));
        assertThat(provider.examUsagesOf(question)).extracting(QuestionUsageProvider.ExamUsageRef::blockId)
                .contains("pool-block");
    }

    @Test
    void inheritedScopeAndSubjectChoiceAreReportedByBothEndpoints() throws Exception {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        CatalogItemView leaf = knowledge.stream().filter(item -> knowledge.stream().anyMatch(parent ->
                !parent.id().equals(item.id()) && item.path().startsWith(parent.path()))).findFirst().orElseThrow();
        CatalogItemView parent = knowledge.stream().filter(item -> !item.id().equals(leaf.id())
                && leaf.path().startsWith(item.path())).findFirst().orElseThrow();
        UUID subject = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) values (?,?,'SINGLE_CHOICE','READY',?,1,1)", question, "USAGE-" + question, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", question);
        jdbc.update("insert into questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) values (?,?,true)", question, leaf.id());
        UUID exam = UUID.randomUUID();
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing,kind_item_id,has_unpublished_changes) values (?,?,?,'Usage exam','FULL','SHARED','00000000-0000-7000-8200-000000000101',true)", exam, exam.toString(), exam.toString());
        // The options are BRANCH (chosen before the attempt starts): E24 does not apply, so the partial publications
        // below stay partial. A SUBJECT_CHOICE option would require every valid path in each publish (BS 8).
        String json = """
                {"parts":[{"id":"p","name":"Part","participation":{"mode":"CHOICE","choose":1},"sections":[
                  {"id":"s","subjectCode":"TEST","knowledgeScope":{"nodeIds":["%s"]},
                   "blocks":[{"id":"inherited","name":"Inherited","questionType":"SINGLE_CHOICE"},
                             {"id":"wrong-type","name":"Wrong type","questionType":"SHORT_ANSWER"}]}],
                  "options":[{"id":"choice","code":"SCIENCE","name":"Science","kind":"BRANCH",
                    "sections":[{"id":"choice-s","subjectCode":"TEST","blocks":[{"id":"choice-b","name":"Choice block","questionType":"SINGLE_CHOICE"}]}]},
                    {"id":"draft-option","name":"Draft","kind":"BRANCH",
                     "sections":[{"id":"draft-s","subjectCode":"TEST","blocks":[{"id":"draft-b","name":"Draft block","questionType":"SINGLE_CHOICE"}]}]}]}]}
                """.formatted(parent.id());
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        var resolver = new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null);
        refs.saveAllAndFlush(projector.project(exam, document.parts(), resolver));
        UUID unrelatedExam = UUID.randomUUID();
        UUID unrelatedSubject = UUID.randomUUID();
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing) values (?,?,?,'Unrelated','FULL','SHARED')",
                unrelatedExam, unrelatedExam.toString(), unrelatedExam.toString());
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')",
                unrelatedExam, json);
        var unrelatedResolver = new ExamBlockRefProjector.CodeResolver(code -> unrelatedSubject, code -> null,
                code -> null);
        refs.saveAllAndFlush(projector.project(unrelatedExam, document.parts(), unrelatedResolver));

        var actual = provider.examUsagesOf(question);
        assertThat(actual).extracting(QuestionUsageProvider.ExamUsageRef::blockId)
                .containsExactlyInAnyOrder("inherited", "choice-b", "draft-b");
        assertThat(actual).filteredOn(ref -> ref.blockId().equals("choice-b")).singleElement().satisfies(ref -> {
            assertThat(ref.examId()).isEqualTo(exam);
            assertThat(ref.examName()).isEqualTo("Usage exam");
            assertThat(ref.partName()).isEqualTo("Part");
            assertThat(ref.optionId()).isEqualTo("choice");
            assertThat(ref.optionName()).isEqualTo("Science");
            assertThat(ref.blockName()).isEqualTo("Choice block");
        });
        for (String endpoint : List.of("usage", "retire-impact")) {
            var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/" + question + "/" + endpoint)
                    .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            String field = endpoint.equals("usage") ? "sourceBlocks" : "affectedBlocks";
            assertThat(response.path(field)).hasSize(3);
            assertThat(response.path(field).toString()).contains("inherited", "choice-b", "draft-b", "Science")
                    .doesNotContain("wrong-type");
        }
        // A partial publication preserves the old snapshot even when the working structure is removed.
        UUID witness = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) values (?,?,'SINGLE_CHOICE','READY',?,1,1)", witness, "WITNESS-" + witness, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", witness);
        publish(exam, subject, witness, List.of("choice", "draft-option"));
        assertThat(provider.examUsagesOf(question)).extracting(QuestionUsageProvider.ExamUsageRef::blockId)
                .containsExactlyInAnyOrder("inherited", "choice-b", "draft-b");
        UUID replacementSubject = UUID.randomUUID();
        UUID replacementWitness = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) values (?,?,'SINGLE_CHOICE','READY',?,1,1)",
                replacementWitness, "WITNESS-" + replacementWitness, replacementSubject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", replacementWitness);
        jdbc.update("update exam.exam_structure set parts=?::jsonb,structure_hash='test2' where exam_id=?", json.replace("TEST", "OTHER"), exam);
        jdbc.update("update exam.exam_block_ref set subject_item_id=? where exam_id=? and version_no is null", replacementSubject, exam);
        jdbc.update("update exam.exam set has_unpublished_changes=true where id=?", exam);
        publish(exam, replacementSubject, replacementWitness, List.of("choice"));
        for (String endpoint : List.of("usage", "retire-impact")) {
            var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/" + question + "/" + endpoint)
                    .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            String field = endpoint.equals("usage") ? "sourceBlocks" : "affectedBlocks";
            assertThat(response.path(field).toString()).contains("inherited", "draft-b").doesNotContain("choice-b");
        }
        publish(exam, replacementSubject, replacementWitness, List.of("draft-option"));
        jdbc.update("update exam.exam set has_unpublished_changes=true where id=?", exam);
        entityManager.clear();
        mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/versions").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingCopy.unpublishedPaths").isEmpty());
        for (String endpoint : List.of("usage", "retire-impact")) {
            var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/" + question + "/" + endpoint)
                    .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(response.path(endpoint.equals("usage") ? "sourceBlocks" : "affectedBlocks")).isEmpty();
        }
        UUID usedQuestion = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) values (?,?,'SINGLE_CHOICE','READY',?,1,1)",
                usedQuestion, "USAGE-USED-" + usedQuestion, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", usedQuestion);
        jdbc.update("insert into questionbank.question_usage_ledger(question_id,version_no,kind,exam_id) values (?,1,'PREVIEW',?)",
                usedQuestion, exam);
        mockMvc.perform(delete("/api/v1/admin/questions/{id}", usedQuestion)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .param("expectedRevision", "0")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));
        mockMvc.perform(delete("/api/v1/admin/questions/{id}", question)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .param("expectedRevision", "0")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select deleted_at is not null from questionbank.question where id = ?",
                Boolean.class, question)).isTrue();
    }

    @Test
    void competencyOnlyBlockUsageStaysInsideTheSectionTopic() throws Exception {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        CatalogItemView leaf = knowledge.stream().filter(item -> knowledge.stream().anyMatch(parent ->
                !parent.id().equals(item.id()) && item.path().startsWith(parent.path()))).findFirst().orElseThrow();
        UUID competency = UUID.fromString("00000000-0000-7000-8400-000000000903");
        UUID subject = UUID.randomUUID();
        UUID inside = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        for (UUID question : List.of(inside, outside)) {
            jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,competency_item_id,current_version_no,points) values (?,?,'SINGLE_CHOICE','READY',?,?,1,1)",
                    question, "COMP-" + question, subject, competency);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", question);
        }
        jdbc.update("insert into questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) values (?,?,true)", inside, leaf.id());
        UUID exam = UUID.randomUUID();
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing,kind_item_id,has_unpublished_changes) values (?,?,?,'Competency usage','FULL','SHARED','00000000-0000-7000-8200-000000000101',true)", exam, exam.toString(), exam.toString());
        String json = """
                {"parts":[{"id":"p","name":"Part","sections":[
                  {"id":"s","subjectCode":"TEST","knowledgeScope":{"nodeIds":["%s"]},
                   "blocks":[{"id":"competency-only","name":"Competency only","questionType":"SINGLE_CHOICE",
                              "knowledgeScope":{"nodeIds":[],"competencyCodes":["VACT-TOAN"]}}]}]}]}
                """.formatted(leaf.id());
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        var resolver = new ExamBlockRefProjector.CodeResolver(code -> subject, code -> competency, code -> null);
        refs.saveAllAndFlush(projector.project(exam, document.parts(), resolver));
        assertThat(provider.examUsagesOf(inside)).extracting(QuestionUsageProvider.ExamUsageRef::blockId)
                .containsExactly("competency-only");
        assertThat(provider.examUsagesOf(outside)).isEmpty();
    }

    @Test
    void strictSlotScopesApplyToUsageAndRetireImpactForRootsAndChildren() throws Exception {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        var topic = knowledge.stream().filter(a -> knowledge.stream().anyMatch(b ->
                !b.path().startsWith(a.path()) && !a.path().startsWith(b.path()))).findFirst().orElseThrow();
        var other = knowledge.stream().filter(item -> !item.path().startsWith(topic.path())
                && !topic.path().startsWith(item.path())).findFirst().orElseThrow();
        UUID subject = UUID.randomUUID();
        UUID exam = UUID.randomUUID();
        UUID mixed = UUID.randomUUID();
        UUID clean = UUID.randomUUID();
        UUID mixedA = UUID.randomUUID();
        UUID mixedB = UUID.randomUUID();
        UUID cleanA = UUID.randomUUID();
        UUID cleanB = UUID.randomUUID();
        for (UUID root : List.of(mixed, clean)) {
            jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) values (?,?,'QUESTION_SET','READY',?,1)",
                    root, "SCOPE-" + root, subject);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", root);
        }
        for (UUID child : List.of(mixedA, mixedB, cleanA, cleanB)) {
            UUID root = List.of(mixedA, mixedB).contains(child) ? mixed : clean;
            jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) values (?,?,'SINGLE_CHOICE','READY',?,?,1)",
                    child, "SCOPE-" + child, root, List.of(mixedB, cleanB).contains(child) ? 1 : 0);
            jdbc.update("insert into questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) values (?,?,true)",
                    child, child.equals(mixedB) ? other.id() : topic.id());
        }
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing,has_unpublished_changes) values (?,?,?,'Scope exam','FULL','SHARED',true)",
                exam, exam.toString(), exam.toString());
        String json = """
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"TEST","blocks":[
                  {"id":"strict-slot","questionType":"QUESTION_SET","slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}}]},
                  {"id":"strict-parent","questionType":"QUESTION_SET","knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}},
                  {"id":"legacy","questionType":"QUESTION_SET","slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"]}}]},
                  {"id":"any-slot","questionType":"QUESTION_SET","slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}},{"index":2}]}
                ]}]}]}
                """.formatted(topic.id());
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        refs.saveAllAndFlush(projector.project(exam, document.parts(),
                new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null)));
        for (UUID question : List.of(mixed, mixedA, mixedB, clean, cleanA, cleanB)) {
            boolean allInScope = List.of(clean, cleanA, cleanB).contains(question);
            for (String endpoint : List.of("usage", "retire-impact")) {
                var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/" + question + "/" + endpoint)
                        .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
                String blocks = response.path(endpoint.equals("usage") ? "sourceBlocks" : "affectedBlocks").toString();
                assertThat(blocks).contains("legacy", "any-slot");
                if (allInScope) assertThat(blocks).contains("strict-slot", "strict-parent");
                else assertThat(blocks).doesNotContain("strict-slot", "strict-parent");
            }
        }
    }

    @Test
    void unconfiguredSlotIndexUsesTheGroupConditionsForUsageAndRetireImpact() throws Exception {
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        var topic = knowledge.stream().filter(a -> knowledge.stream().anyMatch(b ->
                !b.path().startsWith(a.path()) && !a.path().startsWith(b.path()))).findFirst().orElseThrow();
        var other = knowledge.stream().filter(item -> !item.path().startsWith(topic.path())
                && !topic.path().startsWith(item.path())).findFirst().orElseThrow();
        UUID subject = UUID.randomUUID();
        UUID exam = UUID.randomUUID();
        UUID mixed = UUID.randomUUID();
        UUID mixedA = UUID.randomUUID();
        UUID mixedB = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) values (?,?,'QUESTION_SET','READY',?,1)",
                mixed, "SPARSE-" + mixed, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", mixed);
        for (UUID child : List.of(mixedA, mixedB)) {
            jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) values (?,?,'SINGLE_CHOICE','READY',?,?,1)",
                    child, "SPARSE-" + child, mixed, child.equals(mixedB) ? 1 : 0);
            jdbc.update("insert into questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) values (?,?,true)",
                    child, child.equals(mixedB) ? other.id() : topic.id());
        }
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing,has_unpublished_changes) values (?,?,?,'Sparse slots','FULL','SHARED',true)",
                exam, exam.toString(), exam.toString());
        String json = """
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"TEST","blocks":[
                  {"id":"sparse","questionType":"QUESTION_SET","setCount":2,"setSize":2,
                   "slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}}]},
                  {"id":"all-strict","questionType":"QUESTION_SET","setCount":2,"setSize":2,
                   "slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}},
                            {"index":2,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}}]},
                  {"id":"huge","questionType":"QUESTION_SET","setCount":1000000000,"setSize":2,
                   "slots":[{"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}},
                            {"index":1,"knowledgeScope":{"nodeIds":["%1$s"],"everyChildInScope":true}}]}
                ]}]}]}
                """.formatted(topic.id());
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        refs.saveAllAndFlush(projector.project(exam, document.parts(),
                new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null)));
        for (UUID question : List.of(mixed, mixedA, mixedB)) {
            for (String endpoint : List.of("usage", "retire-impact")) {
                var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/" + question + "/" + endpoint)
                        .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
                String blocks = response.path(endpoint.equals("usage") ? "sourceBlocks" : "affectedBlocks").toString();
                assertThat(blocks).contains("sparse", "huge").doesNotContain("all-strict");
            }
        }
    }

    private static final String SC4 = choice(4);
    private static final String SC5 = choice(5);
    private static final String TF2 = "{\"type\":\"TRUE_FALSE\",\"statements\":[{\"answer\":true},{\"answer\":false}]}";

    private static String choice(int options) {
        var body = new StringBuilder("{\"type\":\"SINGLE_CHOICE\",\"options\":[");
        for (int i = 0; i < options; i++) {
            body.append(i == 0 ? "" : ",").append("{\"id\":\"o").append(i).append("\"")
                    .append(i == 0 ? ",\"correct\":true" : "").append('}');
        }
        return body.append("]}").toString();
    }

    private UUID usageQuestion(UUID subject, String type, String body, boolean withAudio) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) values (?,?,?,'READY',?,1,1)",
                id, "PARITY-" + id, type, subject);
        String attachments = "[]";
        if (withAudio) {
            UUID asset = UUID.randomUUID();
            jdbc.update("insert into questionbank.question_asset(id,kind,object_key,file_name,mime,size,sha256) values (?,'ATTACHMENT',?,?,'audio/mpeg',1,?)",
                    asset, "k/" + asset, "f-" + asset, new byte[32]);
            attachments = "[\"" + asset + "\"]";
        }
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,?::jsonb)",
                id, "{\"body\":" + body + ",\"attachmentIds\":" + attachments + "}");
        return id;
    }

    /** A question set whose children carry the given types and bodies, one per position. */
    private List<UUID> usageSet(UUID subject, List<String[]> children) {
        UUID root = usageQuestion(subject, "QUESTION_SET", "{\"type\":\"QUESTION_SET\",\"children\":[]}", false);
        var ids = new java.util.ArrayList<UUID>(List.of(root));
        for (int position = 0; position < children.size(); position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) values (?,?,?,'READY',?,?,1)",
                    child, "PARITY-" + child, children.get(position)[0], root, position);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,?::jsonb)",
                    child, "{\"body\":" + children.get(position)[1] + "}");
            ids.add(child);
        }
        return ids;
    }

    private void usageExam(UUID subject, String sectionsJson) throws Exception {
        UUID exam = UUID.randomUUID();
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing,has_unpublished_changes) values (?,?,?,'Parity exam','FULL','SHARED',true)",
                exam, exam.toString(), exam.toString());
        String json = "{\"parts\":[{\"id\":\"p\",\"name\":\"Part\",\"sections\":" + sectionsJson + "}]}";
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        refs.saveAllAndFlush(projector.project(exam, document.parts(),
                new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null)));
    }

    /** Both reverse lookups list exactly the blocks generation could select this question for. */
    private void assertUsageBlocks(UUID question, List<String> listed, List<String> notListed) throws Exception {
        for (String endpoint : List.of("usage", "retire-impact")) {
            var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/questions/" + question + "/" + endpoint)
                    .with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            String blocks = response.path(endpoint.equals("usage") ? "sourceBlocks" : "affectedBlocks").toString();
            for (String block : listed) assertThat(blocks).as(endpoint + " lists " + block).contains(block);
            for (String block : notListed) assertThat(blocks).as(endpoint + " omits " + block).doesNotContain(block);
        }
    }

    /** R1-6: the section's audio requirement reaches the reverse lookup, as it reaches generation. */
    @Test
    void usageAndRetireImpactApplySectionAudioRequirement() throws Exception {
        UUID subject = UUID.randomUUID();
        UUID withAudio = usageQuestion(subject, "SINGLE_CHOICE", SC4, true);
        UUID plain = usageQuestion(subject, "SINGLE_CHOICE", SC4, false);
        usageExam(subject, """
                [{"id":"s-needs","subjectCode":"TEST","audioRequirement":"REQUIRED",
                  "blocks":[{"id":"needs-audio","questionType":"SINGLE_CHOICE"}]},
                 {"id":"s-forbids","subjectCode":"TEST","audioRequirement":"FORBIDDEN",
                  "blocks":[{"id":"forbids-audio","questionType":"SINGLE_CHOICE"}]},
                 {"id":"s-any","subjectCode":"TEST",
                  "blocks":[{"id":"any-audio","questionType":"SINGLE_CHOICE"}]}]""");
        assertUsageBlocks(withAudio, List.of("needs-audio", "any-audio"), List.of("forbids-audio"));
        assertUsageBlocks(plain, List.of("forbids-audio", "any-audio"), List.of("needs-audio"));
    }

    /** R1-6: a question whose option count differs from itemShape.options is not listed. */
    @Test
    void usageAndRetireImpactApplyOptionCount() throws Exception {
        UUID subject = UUID.randomUUID();
        UUID four = usageQuestion(subject, "SINGLE_CHOICE", SC4, false);
        UUID five = usageQuestion(subject, "SINGLE_CHOICE", SC5, false);
        usageExam(subject, """
                [{"id":"s","subjectCode":"TEST","blocks":[
                  {"id":"four-options","questionType":"SINGLE_CHOICE","itemShape":{"options":4}},
                  {"id":"free-options","questionType":"SINGLE_CHOICE"}]}]""");
        assertUsageBlocks(four, List.of("four-options", "free-options"), List.of());
        assertUsageBlocks(five, List.of("free-options"), List.of("four-options"));
    }

    /** R2-1: a block restricted to INTEGER answers does not list a DECIMAL short answer. */
    @Test
    void usageAndRetireImpactApplyAnswerFormats() throws Exception {
        UUID subject = UUID.randomUUID();
        UUID integer = usageQuestion(subject, "SHORT_ANSWER",
                "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"INTEGER\",\"answers\":[\"1\"]}", false);
        UUID decimal = usageQuestion(subject, "SHORT_ANSWER",
                "{\"type\":\"SHORT_ANSWER\",\"answerFormat\":\"DECIMAL\",\"answers\":[\"1.2\"]}", false);
        usageExam(subject, """
                [{"id":"s","subjectCode":"TEST","blocks":[
                  {"id":"integer-only","questionType":"SHORT_ANSWER","answerFormats":["INTEGER"]},
                  {"id":"any-format","questionType":"SHORT_ANSWER"}]}]""");
        assertUsageBlocks(integer, List.of("integer-only", "any-format"), List.of());
        assertUsageBlocks(decimal, List.of("any-format"), List.of("integer-only"));
    }

    /** R1-6: a set failing a positional child condition is not listed, nor are its children. */
    @Test
    void usageAndRetireImpactApplyPositionalChildConditions() throws Exception {
        UUID subject = UUID.randomUUID();
        var matching = usageSet(subject, List.of(new String[] {"TRUE_FALSE", TF2},
                new String[] {"TRUE_FALSE", TF2}, new String[] {"SINGLE_CHOICE", SC4}));
        var failing = usageSet(subject, List.of(new String[] {"SINGLE_CHOICE", SC4},
                new String[] {"TRUE_FALSE", TF2}, new String[] {"SINGLE_CHOICE", SC4}));
        var wrongShape = usageSet(subject, List.of(new String[] {"TRUE_FALSE", TF2},
                new String[] {"TRUE_FALSE", TF2}, new String[] {"SINGLE_CHOICE", SC5}));
        usageExam(subject, """
                [{"id":"s","subjectCode":"TEST","blocks":[
                  {"id":"typed-positions","questionType":"QUESTION_SET","setCount":1,"setSize":3,
                   "childConditions":[{"from":1,"to":2,"answerType":"TRUE_FALSE"},
                                      {"from":3,"to":3,"answerType":"SINGLE_CHOICE","itemShape":{"options":4}}]},
                  {"id":"open-positions","questionType":"QUESTION_SET","setCount":1,"setSize":3}]}]""");
        for (UUID id : matching) assertUsageBlocks(id, List.of("typed-positions", "open-positions"), List.of());
        for (UUID id : failing) assertUsageBlocks(id, List.of("open-positions"), List.of("typed-positions"));
        for (UUID id : wrongShape) assertUsageBlocks(id, List.of("open-positions"), List.of("typed-positions"));
    }

    private void publish(UUID exam, UUID subject, UUID witness, List<String> selected) throws Exception {
        entityManager.clear();
        var examEntity = exams.findById(exam).orElseThrow();
        var profile = profiles.findById(examEntity.getKindItemId()).orElseThrow();
        String hash = jdbc.queryForObject("select structure_hash from exam.exam_structure where exam_id=?", String.class, exam);
        var input = new ExamCheckResultStore.Input(exam, examEntity.getRevision(), hash,
                mapper.valueToTree(ExamCheckFingerprint.Config.from(examEntity)), profile.getRevision(), catalog.revision(),
                "test", mapper.valueToTree(source.sourceRevisions(List.of(subject))));
        var outcome = mapper.createObjectNode().put("status", "VALID");
        var paths = outcome.putArray("paths");
        var witnesses = mapper.createArrayNode();
        for (String id : List.of("choice", "draft-option")) {
            paths.addObject().put("pathId", id).put("result", "VALID");
            var path = witnesses.addObject();
            path.putObject("path").putArray("chosen").addObject().put("optionId", id);
            path.putArray("witness").addObject().put("questionId", witness.toString()).put("versionNo", 1);
        }
        assertThat(checks.save(input, "VALID", 0, Instant.now(), 1, outcome, witnesses)).isTrue();
        ExamListingFixture.fill(jdbc, exam);
        var request = mapper.createObjectNode().put("expectedRevision", examEntity.getRevision());
        request.set("pathIds", mapper.valueToTree(selected));
        mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish")
                .with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        entityManager.clear();
    }

    @Test
    void descriptionsBatchServingRootAndChildrenAndExcludeRetired() {
        UUID subject = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID retired = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) values (?,?,'QUESTION_SET','READY',?,1)",
                root, "USAGE-SET-" + root, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", root);
        jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position) values (?,?,'SHORT_ANSWER','READY',?,0)",
                child, "USAGE-CHILD-" + child, root);
        jdbc.update("insert into questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) values (?,?,true)", child, node);
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) values (?,?,'SINGLE_CHOICE','RETIRED',?,1)",
                retired, "USAGE-RETIRED-" + retired, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", retired);
        var descriptions = source.describe(List.of(root, child, retired, UUID.randomUUID()));
        assertThat(descriptions.keySet()).containsExactlyInAnyOrder(root, child);
        assertThat(descriptions.get(child).parentId()).isEqualTo(root);
        assertThat(descriptions.get(child).subjectId()).isEqualTo(subject);
        assertThat(descriptions.get(child).questionType()).isEqualTo(QuestionType.QUESTION_SET);
        assertThat(descriptions.get(child).answerTypes()).containsExactly(QuestionType.SHORT_ANSWER);
        assertThat(descriptions.get(root).knowledgeIds()).containsExactly(node);
    }

    @Test
    void boundedMatchingAgreesWithCandidatesForRootAndChildFilters() {
        UUID subject = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID k1 = UUID.randomUUID();
        UUID k2 = UUID.randomUUID();
        UUID tag = UUID.randomUUID();
        String tagCode = "USAGE_" + tag.toString().replace("-", "");
        jdbc.update("insert into catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,display_order,status,is_system,depth,path) values (?, '00000000-0000-7000-8100-000000000008',?,?,(select coalesce(max(value), 0) + 1 from catalog.catalog_item where group_id = '00000000-0000-7000-8100-000000000008'),'Usage tag',0,1,false,1,?)",
                tag, tagCode, tagCode.toLowerCase(), "/" + tag + "/");
        UUID task = catalog.findActiveItem("TASK_TYPE", "CLUSTER").orElseThrow().id();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,task_type_item_id) values (?,?,'QUESTION_SET','READY',?,1,?)",
                root, "PARITY-" + root, subject, task);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", root);
        jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) values (?,?,'SHORT_ANSWER','READY',?,0,1)",
                child, "PARITY-" + child, root);
        jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", root, k1);
        jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", child, k2);
        jdbc.update("insert into questionbank.question_tag(question_id,tag_item_id) values (?,?)", root, tag);
        var good = new QuestionSourcePort.Filter(subject, List.of(List.of(UUID.randomUUID(), k1), List.of(k2)),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER",
                QuestionType.SHORT_ANSWER, List.of(tagCode), List.of(), java.math.BigDecimal.ONE, null, null, null);
        var wrongPoints = new QuestionSourcePort.Filter(subject, good.knowledgeGroups(),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER", QuestionType.SHORT_ANSWER,
                List.of(tagCode), List.of(), java.math.BigDecimal.TEN, null, null, null);
        var wrongSubject = new QuestionSourcePort.Filter(UUID.randomUUID(), good.knowledgeGroups(),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER", QuestionType.SHORT_ANSWER,
                List.of(tagCode), List.of(), java.math.BigDecimal.ONE, null, null, null);
        var wrongTag = new QuestionSourcePort.Filter(subject, good.knowledgeGroups(),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER", QuestionType.SHORT_ANSWER,
                List.of("MISSING"), List.of(), java.math.BigDecimal.ONE, null, null, null);
        var wrongTask = new QuestionSourcePort.Filter(subject, good.knowledgeGroups(),
                List.of(), QuestionType.QUESTION_SET, "SINGLE", QuestionType.SHORT_ANSWER,
                List.of(tagCode), List.of(), java.math.BigDecimal.ONE, null, null, null);
        for (var filter : List.of(good, wrongPoints, wrongSubject, wrongTag, wrongTask)) {
            var candidates = source.findCandidates(filter, 100);
            var expected = new java.util.HashSet<UUID>();
            for (var candidate : candidates) {
                expected.add(candidate.questionId());
                candidate.units().forEach(unit -> expected.add(unit.questionId()));
            }
            expected.retainAll(List.of(root, child, missing));
            assertThat(source.matching(filter, List.of(root, child, missing))).isEqualTo(expected);
        }
        assertThat(source.matching(good, List.of(root, child, missing))).containsExactlyInAnyOrder(root, child);
        assertThat(source.matching(good, List.of())).isEmpty();
    }
}
