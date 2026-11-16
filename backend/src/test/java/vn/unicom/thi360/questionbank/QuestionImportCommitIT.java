package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

class QuestionImportCommitIT extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/admin/question-imports/uploads";
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectStorage storage;
    @MockitoSpyBean private AuditRecorder audit;

    @Test
    void commitReplaysAfterFileDeletedAndRejectsChangedBody() throws Exception {
        UUID actor = UUID.randomUUID();
        String source = ("IMPORT-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer\n"
                + source + ",SHORT_ANSWER,How many?,TOAN,G10,NB,42\n";
        var uploaded = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "questions.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        UUID id = UUID.fromString(mapper.readTree(uploaded.getResponse().getContentAsByteArray()).get("id").asText());
        String objectKey = jdbc.queryForObject("select object_key from questionbank.question_import_upload where id = ?", String.class, id);
        String request = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer"},"duplicatePolicy":"SKIP","skipErrors":false}
                """.formatted(id);
        String key = UUID.randomUUID().toString();
        var committed = mockMvc.perform(post(URL + "/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.singleCount").value(1))
                .andReturn();
        JsonNode result = mapper.readTree(committed.getResponse().getContentAsByteArray());
        assertThat(storage.exists(objectKey)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where source_code_normalized = ?", Integer.class, source)).isEqualTo(1);
        var replay = mockMvc.perform(post(URL + "/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(replay.getResponse().getContentAsByteArray())).isEqualTo(result);
        mockMvc.perform(post(URL + "/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("\"skipErrors\":false", "\"skipErrors\":true")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mockMvc.perform(post(URL + "/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mockMvc.perform(post(URL + "/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_ALREADY_COMMITTED"));
        mockMvc.perform(post(URL + "/{id}/validate", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_SOURCE_GONE"));
        verify(audit, times(1)).record(argThat(event -> "QUESTION.IMPORT".equals(event.action())
                && Integer.valueOf(1).equals(event.after().get("createdCount"))
                && !event.after().toString().contains("<")));
    }

    @Test
    void concurrentCommitsWithSameKeyWriteOnce() throws Exception {
        UUID actor = UUID.randomUUID();
        String source = ("RACE-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer\n"
                + source + ",SHORT_ANSWER,Question,TOAN,G10,NB,42\n";
        var upload = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "race.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        String body = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer"},"duplicatePolicy":"SKIP","skipErrors":false}
                """.formatted(id);
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var call = (java.util.concurrent.Callable<String>) () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post(URL + "/{id}/commit", id)
                                .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            start.countDown();
            assertThat(mapper.readTree(first.get(30, TimeUnit.SECONDS)))
                    .isEqualTo(mapper.readTree(second.get(30, TimeUnit.SECONDS)));
        }
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where source_code_normalized = ?",
                Integer.class, source)).isEqualTo(1);
    }

    @Test
    void concurrentCommitsWithDifferentKeysWriteOnce() throws Exception {
        UUID actor = UUID.randomUUID();
        String source = ("RACE-KEYS-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer\n"
                + source + ",SHORT_ANSWER,Question,TOAN,G10,NB,42\n";
        var upload = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "race-keys.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        String body = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer"},"duplicatePolicy":"SKIP","skipErrors":false}
                """.formatted(id);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var call = (java.util.concurrent.Callable<Integer>) () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(post(URL + "/{id}/commit", id)
                                .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            start.countDown();
            assertThat(java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where source_code_normalized = ?",
                Integer.class, source)).isEqualTo(1);
    }

    @Test
    void midCommitFailureRollsBackQuestionsHistoryAndKeepsFile() throws Exception {
        UUID actor = UUID.randomUUID();
        String source = ("ROLLBACK-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String fileName = source + ".csv";
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer\n"
                + source + ",SHORT_ANSWER,Question,TOAN,G10,NB,42\n";
        var upload = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        String objectKey = jdbc.queryForObject("select object_key from questionbank.question_import_upload where id = ?",
                String.class, UUID.fromString(id));
        String body = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer"},"duplicatePolicy":"SKIP","skipErrors":false}
                """.formatted(id);
        doThrow(new IllegalStateException("forced audit failure")).when(audit)
                .record(argThat(event -> "QUESTION.IMPORT".equals(event.action())));
        mockMvc.perform(post(URL + "/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where source_code_normalized = ?",
                Integer.class, source)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_import where file_name = ?",
                Integer.class, fileName)).isZero();
        assertThat(jdbc.queryForObject("select status from questionbank.question_import_upload where id = ?",
                String.class, UUID.fromString(id))).isEqualTo("UPLOADED");
        assertThat(storage.exists(objectKey)).isTrue();
    }

    @Test
    void setWithUnsafeChildIsDroppedWithSkipErrors() throws Exception {
        String setCode = ("SET-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String validCode = ("OK-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer,setCode\n"
                + setCode + ",QUESTION_SET,Passage,TOAN,G10,NB,,group\n"
                + ",SHORT_ANSWER,<script>alert(1)</script>,,,,,group\n"
                + validCode + ",SHORT_ANSWER,Good,TOAN,G10,NB,42,\n";
        var upload = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "sets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        String body = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer","c7":"setCode"},"duplicatePolicy":"SKIP","skipErrors":true}
                """.formatted(id);
        mockMvc.perform(post(URL + "/{id}/commit", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.errorRowCount").value(2));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where source_code_normalized = ?",
                Integer.class, setCode)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question where source_code_normalized = ?",
                Integer.class, validCode)).isEqualTo(1);
    }
}
