package vn.unicom.thi360.questionbank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class QuestionApiEdgesIT extends AbstractIntegrationTest {
    private static final String BASE = "/api/v1/admin/questions";
    @Test
    void diacriticInsensitive() throws Exception {
        UUID actor = UUID.randomUUID();
        String body = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p>Toán và dao động</p>\",\"body\":"
                + QuestionTestSupport.singleChoiceBody() + "}";
        var response = mockMvc.perform(post(BASE)
                        .with(asUser(actor, "search-author", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(response.getResponse().getContentAsString()).at("/summary/id").asText();
        for (String keyword : new String[] {"toan", "dao dong"}) {
            mockMvc.perform(get(BASE).param("createdBy", actor.toString()).param("keyword", keyword)
                            .with(asUser(Permissions.QUESTION_VIEW)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", hasItem(id)));
        }
    }

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void imgRejectedWithoutAssetResolver() throws Exception {
        String body = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"<p><img data-asset-id='"
                + UUID.randomUUID() + "' src='x'></p>\",\"body\":" + QuestionTestSupport.singleChoiceBody() + "}";
        create(body).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_CONTENT_UNSAFE"));
    }

    @Test
    void nestedSetOrClozeRejected() throws Exception {
        for (String type : new String[] {"QUESTION_SET", "CLOZE"}) {
            String childBody = type.equals("CLOZE")
                    ? "{\"type\":\"CLOZE\",\"passage\":\"\",\"blanks\":[]}"
                    : "{\"type\":\"QUESTION_SET\",\"passage\":\"\",\"children\":[]}";
            String child = "{\"type\":\"" + type + "\",\"body\":" + childBody + "}";
            String body = "{\"type\":\"QUESTION_SET\",\"body\":{\"type\":\"QUESTION_SET\",\"children\":["
                    + child + "," + child + "]}}";
            create(body).andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void staleRevision412() throws Exception {
        UUID id = createValid();
        long revision = revision(id);
        String request = "{\"expectedRevision\":" + revision + ","
                + QuestionTestSupport.createSingleChoiceRequest().substring(1);
        mockMvc.perform(put(BASE + "/{id}", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/{id}", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("QUESTION_REVISION_CONFLICT"));
    }

    @Test
    void deleteInUse409() throws Exception {
        UUID id = createValid();
        jdbc.update("INSERT INTO questionbank.question_usage_ledger (question_id, version_no, kind, exam_id) VALUES (?, 1, 'PREVIEW', ?)", id, UUID.randomUUID());
        mockMvc.perform(delete(BASE + "/{id}", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .param("expectedRevision", Long.toString(revision(id)))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));
    }

    private org.springframework.test.web.servlet.ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(BASE).with(asUser(Permissions.QUESTION_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID createValid() throws Exception {
        var response = create(QuestionTestSupport.createSingleChoiceRequest())
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).at("/summary/id").asText());
    }

    private long revision(UUID id) {
        return jdbc.queryForObject("SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);
    }
}
