package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamBlockRefProjector;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

@Transactional
class ExamQuestionUsageIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;
    @Autowired ExamBlockRefProjector projector;
    @Autowired ExamBlockRefRepository refs;
    @Autowired QuestionUsageProvider provider;

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
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing) values (?,?,?,'Usage exam','FULL','SHARED')", exam, exam.toString(), exam.toString());
        String json = """
                {"parts":[{"id":"p","name":"Part","sections":[
                  {"id":"s","subjectCode":"TEST","knowledgeScope":{"nodeIds":["%s"],"match":"ANY"},
                   "blocks":[{"id":"inherited","name":"Inherited","questionType":"SINGLE_CHOICE"},
                             {"id":"wrong-type","name":"Wrong type","questionType":"SHORT_ANSWER"}]}],
                  "options":[{"id":"choice","code":"SCIENCE","name":"Science","kind":"SUBJECT_CHOICE",
                    "sections":[{"id":"choice-s","subjectCode":"TEST","blocks":[{"id":"choice-b","name":"Choice block","questionType":"SINGLE_CHOICE"}]}]}]}]}
                """.formatted(parent.id());
        var document = new ExamStructureCodec(mapper).read(json);
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')", exam, json);
        var resolver = new ExamBlockRefProjector.CodeResolver(code -> subject, code -> null, code -> null,
                code -> null, code -> null);
        refs.saveAllAndFlush(projector.project(exam, document.parts(), resolver));
        UUID unrelatedExam = UUID.randomUUID();
        UUID unrelatedSubject = UUID.randomUUID();
        jdbc.update("insert into exam.exam(id,code,code_normalized,name,scope,timing) values (?,?,?,'Unrelated','FULL','SHARED')",
                unrelatedExam, unrelatedExam.toString(), unrelatedExam.toString());
        jdbc.update("insert into exam.exam_structure(exam_id,parts,structure_hash) values (?,?::jsonb,'test')",
                unrelatedExam, json);
        var unrelatedResolver = new ExamBlockRefProjector.CodeResolver(code -> unrelatedSubject, code -> null,
                code -> null, code -> null, code -> null);
        refs.saveAllAndFlush(projector.project(unrelatedExam, document.parts(), unrelatedResolver));

        var actual = provider.examUsagesOf(question);
        assertThat(actual).extracting(QuestionUsageProvider.ExamUsageRef::blockId)
                .containsExactlyInAnyOrder("inherited", "choice-b");
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
            assertThat(response.path(field)).hasSize(2);
            assertThat(response.path(field).toString()).contains("inherited", "choice-b", "Science")
                    .doesNotContain("wrong-type");
        }
    }
}
