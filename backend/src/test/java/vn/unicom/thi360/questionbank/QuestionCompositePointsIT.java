package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class QuestionCompositePointsIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void clozePointsAreSumOfBlankPointsIncludingDefaults() throws Exception {
        assertDerivedPoints("""
                {"type":"CLOZE","points":99,"body":{"type":"CLOZE",
                 "passage":"<p>[[a]] [[b]]</p>","blanks":[
                 {"id":"a","answers":["x"],"points":0.5},
                 {"id":"b","answers":["y"]}]}}
                """, "1.5");
    }

    @Test
    void setPointsAreSumOfChildPointsIncludingDefaults() throws Exception {
        assertDerivedPoints("""
                {"type":"QUESTION_SET","points":99,"body":{"type":"QUESTION_SET",
                 "passage":"<p>Context</p>","children":[
                 {"type":"SHORT_ANSWER","content":"<p>A</p>","body":{"type":"SHORT_ANSWER","answers":["a"]}},
                 {"type":"SHORT_ANSWER","content":"<p>B</p>","points":2,"body":{"type":"SHORT_ANSWER","answers":["b"]}}]}}
                """, "3");
    }

    private void assertDerivedPoints(String request, String expected) throws Exception {
        var response = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.points").value(new BigDecimal(expected).doubleValue()))
                .andReturn();
        UUID id = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString())
                .at("/summary/id").asText());
        var row = jdbc.queryForMap("""
                SELECT q.points, (v.snapshot ->> 'points')::numeric AS snapshot_points
                FROM questionbank.question q JOIN questionbank.question_version v
                  ON v.question_id = q.id AND v.version_no = q.working_version_no
                WHERE q.id = ?
                """, id);
        assertThat((BigDecimal) row.get("points")).isEqualByComparingTo(expected);
        assertThat((BigDecimal) row.get("snapshot_points")).isEqualByComparingTo(expected);
    }
}
