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
        var resolver = new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null, code -> null);
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
        String json = """
                {"parts":[{"id":"p","name":"Part","participation":{"mode":"CHOICE","choose":1},"sections":[
                  {"id":"s","subjectCode":"TEST","knowledgeScope":{"nodeIds":["%s"]},
                   "blocks":[{"id":"inherited","name":"Inherited","questionType":"SINGLE_CHOICE"},
                             {"id":"wrong-type","name":"Wrong type","questionType":"SHORT_ANSWER"}]}],
                  "options":[{"id":"choice","code":"SCIENCE","name":"Science","kind":"SUBJECT_CHOICE",
                    "sections":[{"id":"choice-s","subjectCode":"TEST","blocks":[{"id":"choice-b","name":"Choice block","questionType":"SINGLE_CHOICE"}]}]},
                    {"id":"draft-option","name":"Draft","kind":"SUBJECT_CHOICE",
                     "sections":[{"id":"draft-s","subjectCode":"TEST","blocks":[{"id":"draft-b","name":"Draft block","questionType":"SINGLE_CHOICE"}]}]}]}]}
                """.formatted(parent.id());
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        var resolver = new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null,
                code -> null);
        refs.saveAllAndFlush(projector.project(exam, document.parts(), resolver));
        UUID unrelatedExam = UUID.randomUUID();
        UUID unrelatedSubject = UUID.randomUUID();
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing) values (?,?,?,'Unrelated','FULL','SHARED')",
                unrelatedExam, unrelatedExam.toString(), unrelatedExam.toString());
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')",
                unrelatedExam, json);
        var unrelatedResolver = new ExamBlockRefProjector.CodeResolver(code -> unrelatedSubject, code -> null,
                code -> null, code -> null);
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
                QuestionSourcePort.Match.ALL, List.of(), QuestionType.QUESTION_SET, "CLUSTER",
                QuestionType.SHORT_ANSWER, List.of(tagCode), List.of(), java.math.BigDecimal.ONE);
        var wrongPoints = new QuestionSourcePort.Filter(subject, good.knowledgeGroups(), good.knowledgeMatch(),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER", QuestionType.SHORT_ANSWER,
                List.of(tagCode), List.of(), java.math.BigDecimal.TEN);
        var wrongSubject = new QuestionSourcePort.Filter(UUID.randomUUID(), good.knowledgeGroups(), good.knowledgeMatch(),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER", QuestionType.SHORT_ANSWER,
                List.of(tagCode), List.of(), java.math.BigDecimal.ONE);
        var wrongTag = new QuestionSourcePort.Filter(subject, good.knowledgeGroups(), good.knowledgeMatch(),
                List.of(), QuestionType.QUESTION_SET, "CLUSTER", QuestionType.SHORT_ANSWER,
                List.of("MISSING"), List.of(), java.math.BigDecimal.ONE);
        var wrongTask = new QuestionSourcePort.Filter(subject, good.knowledgeGroups(), good.knowledgeMatch(),
                List.of(), QuestionType.QUESTION_SET, "SINGLE", QuestionType.SHORT_ANSWER,
                List.of(tagCode), List.of(), java.math.BigDecimal.ONE);
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
