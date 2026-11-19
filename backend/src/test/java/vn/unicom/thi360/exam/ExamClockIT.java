package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;

/** EX-1 AC-35 (plan B5): publish and discontinue read the time from the Clock bean, as the e2e test clock needs. */
@Import(ExamClockIT.FrozenClock.class)
class ExamClockIT extends AbstractExamPublishIT {
    static final Instant NOW = Instant.parse("2031-03-04T05:06:07Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FrozenClock {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void publishAndDiscontinueFollowClock() throws Exception {
        UUID exam = newExam("HSA", "[{\"id\":\"part\",\"options\":[{\"id\":\"choice\",\"sections\":[]}]}]");
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk());
        assertThat(instant("SELECT published_at FROM exam.exam_version WHERE exam_id = ? AND version_no = 1", exam)).isEqualTo(NOW);
        assertThat(instant("SELECT updated_at FROM exam.exam WHERE id = ?", exam)).isEqualTo(NOW);
        entityManager.clear();
        mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/discontinue").with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision(exam) + "}"))
                .andExpect(status().isOk());
        assertThat(instant("SELECT discontinued_at FROM exam.exam_version WHERE exam_id = ? AND version_no = 1", exam)).isEqualTo(NOW);
        assertThat(instant("SELECT updated_at FROM exam.exam WHERE id = ?", exam)).isEqualTo(NOW);
    }

    private Instant instant(String sql, UUID exam) {
        return jdbc.queryForObject(sql, Timestamp.class, exam).toInstant();
    }
}
