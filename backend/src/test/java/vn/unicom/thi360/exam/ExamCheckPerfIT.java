package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Tag("perf")
@Transactional
class ExamCheckPerfIT extends AbstractIntegrationTest {
    @Test
    void seededHsaSyncCheckFinishesWithinThirtySeconds() throws Exception {
        UUID examId = UUID.fromString("00000000-0000-7000-8700-000000000001");
        long start = System.nanoTime();
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeLimitSeconds").value(30))
                .andExpect(jsonPath("$.groups.length()").value(8));
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("PERF ExamCheck seeded HSA ms=" + elapsed);
        assertThat(elapsed).isLessThan(30_000);
    }
}
