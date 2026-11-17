package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RateLimitFilter;

@Tag("perf")
class QuestionImportPerfIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void clearUploadRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.UPLOAD_KEY_PREFIX + "127.0.0.1");
    }

    @Test
    void import2000RowsUnder120s() throws Exception {
        String prefix = "PERF-" + UUID.randomUUID() + "-";
        StringBuilder csv = new StringBuilder("sourceCode,type,content,subject,grade,difficulty,correctAnswer\n");
        for (int i = 0; i < 2000; i++) {
            csv.append(prefix).append(i).append(",SHORT_ANSWER,Question ").append(i).append(",TOAN,G10,NB,42\n");
        }
        long start = System.nanoTime();
        var upload = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "perf.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        String request = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer"},"duplicatePolicy":"SKIP","skipErrors":false}
                """.formatted(id);
        mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/commit", id)
                        .with(asUser(Permissions.QUESTION_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(2000));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Question import 2000 rows elapsedMs=" + elapsed);
        assertThat(Duration.ofMillis(elapsed)).isLessThan(Duration.ofSeconds(120));
    }
}
