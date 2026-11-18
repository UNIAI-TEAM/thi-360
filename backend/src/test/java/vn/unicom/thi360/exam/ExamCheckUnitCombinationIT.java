package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogCombinationSubjectView;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamCheckUnitCombinationIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private CatalogLookup catalog;

    @Test
    void missingCodeSubjectIsReportedInsteadOfFailingInTheBuilder() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID kindId = jdbc.queryForObject("SELECT kind_item_id FROM exam.exam WHERE id = ?", UUID.class,
                UUID.fromString("00000000-0000-7000-8700-000000000001"));
        var subject = new CatalogCombinationSubjectView(UUID.randomUUID(), "BIOLOGY", "Sinh học", "Biology");
        var combination = new CatalogCombinationView(UUID.randomUUID(), "CODE1", "Code", "Code",
                kindId, "QDA", "QDA", "QDA", null, 1, List.of(subject));
        doReturn(List.of(combination)).when(catalog).listCombinations(kindId);
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,kind_item_id) "
                + "VALUES (?,?,?,'Code check','FULL','SHARED',?)", examId, examId.toString(), examId.toString(), kindId);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,'test')", examId, """
                {"parts":[{"id":"choice","name":"Choice","questionCount":1,
                  "codeSource":{"source":"GLOBAL_LIST"},"subjectPool":[]}]}
                """);
        String body = mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var issues = mapper.readTree(body).path("groups").findValues("issues").stream()
                .flatMap(array -> java.util.stream.StreamSupport.stream(array.spliterator(), false))
                .filter(issue -> "COMBINATION_SUBJECT_MISSING".equals(issue.path("code").asText())).toList();
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().path("subjectCode").asText()).isEqualTo("BIOLOGY");
        assertThat(issues.getFirst().path("affectedCodes").get(0).asText()).isEqualTo("CODE1");
    }
}
