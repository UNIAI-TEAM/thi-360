package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * R1-5: every listing save moves the revision, also when the exam row itself would not change (same editor, same clock
 * instant, only listing columns different, unpublished changes already flagged). Under a fixed clock that case is
 * deterministic: two saves with the same expected revision must end with one 412. Committed rows, one exam per test.
 */
@Import(ExamListingRevisionIT.FrozenClock.class)
class ExamListingRevisionIT extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2031-06-15T03:00:00Z");
    private static final UUID EDITOR = UUID.fromString("00000000-0000-7000-8800-0000000000e1");

    @TestConfiguration(proxyBeanMethods = false)
    static class FrozenClock {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired JdbcTemplate jdbc;
    private final List<UUID> exams = new ArrayList<>();
    private UUID exam;

    @BeforeEach
    void ownExam() {
        exam = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing, has_unpublished_changes) "
                + "VALUES (?, ?, ?, 'Revision test', 'FULL', 'SHARED', true)", exam, exam.toString(), exam.toString());
        exams.add(exam);
    }

    @AfterEach
    void cleanUp() {
        for (UUID owned : exams) {
            jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", owned);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", owned);
        }
    }

    private long revision() {
        return jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
    }

    private long price() {
        return jdbc.queryForObject("SELECT list_price_vnd FROM exam.exam_listing WHERE exam_id = ?", Long.class, exam);
    }

    private int save(long expectedRevision, long priceVnd) throws Exception {
        String body = "{\"summary\":\"same\",\"listPriceVnd\":" + priceVnd + ",\"attemptsGranted\":3,\"description\":\"same\","
                + "\"expectedRevision\":" + expectedRevision + "}";
        return mockMvc.perform(put("/api/v1/admin/exams/" + exam + "/listing")
                        .with(asUser(EDITOR, "editor", Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void everySaveMovesTheRevisionEvenWhenTheExamRowIsUnchanged() throws Exception {
        long r0 = revision();
        assertThat(save(r0, 1_000)).isEqualTo(200);
        long r1 = revision();
        assertThat(r1).isGreaterThan(r0);
        // Same editor, same instant, description and cover unchanged, unpublished changes already flagged: only the
        // listing row differs. The revision must still move.
        assertThat(save(r1, 2_000)).isEqualTo(200);
        long r2 = revision();
        assertThat(r2).as("a save that only changed listing columns").isEqualTo(r1 + 1);
        // The second editor that started from r1 is stale.
        assertThat(save(r1, 3_000)).isEqualTo(412);
        assertThat(price()).isEqualTo(2_000);
        assertThat(revision()).isEqualTo(r2);
    }

    @Test
    void twoSavesFromTheSameRevisionGiveOneConflict() throws Exception {
        long r0 = revision();
        assertThat(save(r0, 1_000)).isEqualTo(200);
        long r1 = revision();
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> saves = new ArrayList<>();
        for (long priceVnd : new long[] {7_000, 8_000}) {
            saves.add(CompletableFuture.supplyAsync(() -> {
                try {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return save(r1, priceVnd);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var save : saves) statuses.add(save.get(30, TimeUnit.SECONDS));
        assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        assertThat(revision()).isEqualTo(r1 + 1);
        assertThat(price()).as("the winner's fields are the persisted ones").isIn(7_000L, 8_000L);
    }
}
