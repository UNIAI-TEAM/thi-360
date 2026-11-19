package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCoverCleanup;
import vn.unicom.thi360.exam.infrastructure.ExamCoverAssetRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * EX-1 AC-30 to AC-32 (plan B5) and the cover races of review R1-2, R1-6, R1-12. The sweep is called directly (the
 * schedule is off in tests) under a frozen clock, so "exactly 7 days" is exact. It works in its own transactions, so the
 * rows are committed and each test removes only the exams and covers it created: the tables are never assumed empty.
 * The object store is a spy, so "no object" and "one delete per key" are asserted on the store, not on rows.
 */
@Import(ExamCoverCleanupJobIT.FrozenClock.class)
class ExamCoverCleanupJobIT extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2031-06-15T03:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FrozenClock {
        @Bean
        @Primary
        Clock frozenClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ExamCoverCleanup cleanup;
    @Autowired ExamCoverAssetRepository assets;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean ObjectStorage storage;
    private final List<UUID> exams = new ArrayList<>();
    private final List<UUID> covers = new ArrayList<>();
    private UUID exam;

    @BeforeEach
    void ownExam() {
        Mockito.reset(storage);
        exam = newExam();
    }

    @AfterEach
    void removeWhatTheTestCreated() {
        Mockito.reset(storage);
        for (UUID owned : exams) {
            jdbc.update("UPDATE exam.exam SET cover_asset_id = NULL WHERE id = ?", owned);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", owned);
            jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", owned);
        }
        for (UUID cover : covers) {
            storage.delete("exam-covers/" + cover);
            jdbc.update("DELETE FROM exam.exam_cover_asset WHERE id = ?", cover);
        }
        for (UUID owned : exams) jdbc.update("DELETE FROM exam.exam WHERE id = ?", owned);
    }

    private UUID newExam() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing) VALUES (?, ?, ?, 'Cover test', 'FULL', 'SHARED')",
                id, id.toString(), id.toString());
        exams.add(id);
        return id;
    }

    private UUID cover(UUID owner, Instant orphanedAt) {
        UUID id = UUID.randomUUID();
        storage.put("exam-covers/" + id, new byte[] {1, 2, 3}, "image/png");
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height, orphaned_at) "
                + "VALUES (?, ?, ?, 'image/png', 3, 600, 600, ?)", id, owner, "exam-covers/" + id,
                orphanedAt == null ? null : Timestamp.from(orphanedAt));
        covers.add(id);
        Mockito.clearInvocations(storage);
        return id;
    }

    private boolean exists(UUID cover) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_cover_asset WHERE id = ?", Long.class, cover) == 1;
    }

    private static String key(UUID cover) {
        return "exam-covers/" + cover;
    }

    private Instant orphanedAt(UUID cover) {
        Timestamp stamp = jdbc.queryForObject("SELECT orphaned_at FROM exam.exam_cover_asset WHERE id = ?", Timestamp.class, cover);
        return stamp == null ? null : stamp.toInstant();
    }

    @Test
    void removesOnlyAfterSevenDays() {
        UUID sevenDays = cover(exam, NOW.minus(Duration.ofDays(7)));
        UUID sixDays = cover(exam, NOW.minus(Duration.ofDays(6)));
        UUID older = cover(exam, NOW.minus(Duration.ofDays(40)));
        UUID fresh = cover(exam, null);
        assertThat(cleanup.run()).as("at least ours; other rows of the table do not matter").isGreaterThanOrEqualTo(2);
        assertThat(exists(sevenDays)).as("unused for 7 days: row removed").isFalse();
        assertThat(storage.exists(key(sevenDays))).as("and its object").isFalse();
        assertThat(exists(older)).isFalse();
        assertThat(storage.exists(key(older))).isFalse();
        assertThat(exists(sixDays)).as("unused for 6 days: kept").isTrue();
        assertThat(storage.exists(key(sixDays))).isTrue();
        // The first sweep that finds an unused cover only stamps it.
        assertThat(exists(fresh)).isTrue();
        assertThat(orphanedAt(fresh)).isEqualTo(NOW);
        verify(storage, never()).delete(key(sixDays));
        verify(storage, never()).delete(key(fresh));
        cleanup.run();
        assertThat(exists(sixDays)).isTrue();
        assertThat(exists(fresh)).isTrue();
    }

    @Test
    void keepsEveryReferencedCover() {
        Instant longAgo = NOW.minus(Duration.ofDays(30));
        UUID working = cover(exam, longAgo);
        UUID oldVersion = cover(exam, longAgo);
        UUID copyCover = cover(exam, longAgo);
        UUID unused = cover(exam, longAgo);
        jdbc.update("UPDATE exam.exam SET cover_asset_id = ? WHERE id = ?", working, exam);
        // A retired version still shows its cover: the snapshot holds the asset id.
        jdbc.update("INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, "
                + "check_run_id, state, status, published_at, published_by) VALUES (?, ?, 9001, ?::jsonb, '{\"parts\":[]}'::jsonb, "
                + "'{}', '{}', ?, 'RETIRED', 'PUBLISHED', now(), ?)", UUID.randomUUID(), exam,
                "{\"coverAssetId\":\"" + oldVersion + "\",\"listing\":{\"coverAssetId\":\"" + oldVersion + "\"}}", UUID.randomUUID(), UUID.randomUUID());
        // The cover of a copy of the exam is that copy's working cover.
        UUID copy = newExam();
        jdbc.update("UPDATE exam.exam SET cover_asset_id = ? WHERE id = ?", copyCover, copy);
        cleanup.run();
        assertThat(exists(unused)).as("only the unused cover goes").isFalse();
        assertThat(exists(working)).isTrue();
        assertThat(exists(oldVersion)).isTrue();
        assertThat(exists(copyCover)).isTrue();
        verify(storage, times(1)).delete(key(unused));
        for (UUID kept : List.of(working, oldVersion, copyCover)) {
            verify(storage, never()).delete(key(kept));
            assertThat(storage.exists(key(kept))).isTrue();
            assertThat(orphanedAt(kept)).as("a cover that is used again loses its stamp").isNull();
        }
    }

    @Test
    void concurrentRunsDeleteOnce() throws Exception {
        List<UUID> expired = new ArrayList<>();
        for (int i = 0; i < 40; i++) expired.add(cover(exam, NOW.minus(Duration.ofDays(8))));
        // A slow store keeps one sweep inside the object deletes while the other one claims, so the two really overlap.
        doAnswer(call -> {
            Thread.sleep(25);
            return call.callRealMethod();
        }).when(storage).delete(anyString());
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return cleanup.run();
            }));
        }
        start.countDown();
        for (var run : runs) run.get(60, TimeUnit.SECONDS);
        for (UUID cover : expired) {
            assertThat(exists(cover)).isFalse();
            assertThat(storage.exists(key(cover))).isFalse();
            verify(storage, times(1)).delete(key(cover));
        }
    }

    @Test
    void failedObjectDeleteIsRetriedByTheNextSweep() {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(8)));
        doThrow(new IllegalStateException("store down")).doCallRealMethod().when(storage).delete(key(cover));
        cleanup.run();
        assertThat(exists(cover)).as("the row is the only durable key: it stays when the object delete fails").isTrue();
        assertThat(storage.exists(key(cover))).isTrue();
        assertThat(jdbc.queryForObject("SELECT delete_requested_at IS NOT NULL FROM exam.exam_cover_asset WHERE id = ?", Boolean.class, cover))
                .as("the deletion intent is persisted").isTrue();
        cleanup.run();
        assertThat(exists(cover)).isFalse();
        assertThat(storage.exists(key(cover))).isFalse();
        verify(storage, times(2)).delete(key(cover));
    }

    @Test
    void crashedSweepIsRetriedOnceItsLeaseRunsOut() {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(8)));
        // A sweep that died after claiming: marked for deletion, leased until one hour from now.
        jdbc.update("UPDATE exam.exam_cover_asset SET delete_requested_at = ?, delete_claimed_until = ? WHERE id = ?",
                Timestamp.from(NOW.minus(Duration.ofDays(1))), Timestamp.from(NOW.plus(Duration.ofHours(1))), cover);
        cleanup.run();
        assertThat(exists(cover)).as("another sweep still owns it").isTrue();
        verify(storage, never()).delete(key(cover));
        jdbc.update("UPDATE exam.exam_cover_asset SET delete_claimed_until = ? WHERE id = ?", Timestamp.from(NOW.minus(Duration.ofSeconds(1))), cover);
        cleanup.run();
        assertThat(exists(cover)).isFalse();
        verify(storage, times(1)).delete(key(cover));
    }

    @Test
    void coverAttachedAndDetachedBetweenSweepsStartsItsSevenDaysAgain() throws Exception {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(6)));
        listing(exam, cover).andExpect(status().isOk());
        assertThat(orphanedAt(cover)).as("attaching clears the stamp").isNull();
        listing(exam, null).andExpect(status().isOk());
        cleanup.run();
        assertThat(exists(cover)).as("detached again, but its seven days restart at the next sweep").isTrue();
        assertThat(orphanedAt(cover)).isEqualTo(NOW);
        verify(storage, never()).delete(key(cover));
    }

    @Test
    void attachAfterTheSweepDeletedTheCoverIsRefused() throws Exception {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(8)));
        cleanup.run();
        assertThat(exists(cover)).isFalse();
        listing(exam, cover).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_LISTING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("coverAssetId"));
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, exam)).isNull();
    }

    @Test
    void attachOfACoverTheSweepAlreadyClaimedIsRefused() throws Exception {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(8)));
        // The sweep committed its claim and has not deleted the object yet.
        jdbc.update("UPDATE exam.exam_cover_asset SET delete_requested_at = ?, delete_claimed_until = ? WHERE id = ?",
                Timestamp.from(NOW), Timestamp.from(NOW.plus(Duration.ofHours(1))), cover);
        listing(exam, cover).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("coverAssetId"));
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, exam)).isNull();
    }

    /** Lock order 1: the sweep's claim commits while the save is already waiting on the cover row: the save revalidates. */
    @Test
    void saveWaitingOnTheCoverRowRevalidatesAfterTheClaim() throws Exception {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(8)));
        var tx = new TransactionTemplate(transactions);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> claim = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT 1 FROM exam.exam_cover_asset WHERE id = ? FOR UPDATE", cover);
            held.countDown();
            try {
                assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            jdbc.update("UPDATE exam.exam_cover_asset SET delete_requested_at = ? WHERE id = ?", Timestamp.from(NOW), cover);
        }));
        assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<ResultActions> save = CompletableFuture.supplyAsync(() -> {
            try {
                return listing(exam, cover);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        awaitLockWaiter();
        assertThat(save.isDone()).as("the save waits for the row lock").isFalse();
        release.countDown();
        claim.get(30, TimeUnit.SECONDS);
        save.get(30, TimeUnit.SECONDS).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("coverAssetId"));
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, exam)).isNull();
    }

    /** Lock order 2: the save holds the cover row, so the sweep skips it and the cover survives with the exam pointing at it. */
    @Test
    void sweepSkipsACoverWhoseAttachHoldsTheRow() throws Exception {
        UUID cover = cover(exam, NOW.minus(Duration.ofDays(8)));
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            assertThat(assets.lockAttachable(cover, exam)).isPresent();
            jdbc.update("UPDATE exam.exam SET cover_asset_id = ? WHERE id = ?", cover, exam);
            assets.clearOrphaned(cover);
            CompletableFuture<Integer> sweep = CompletableFuture.supplyAsync(cleanup::run);
            try {
                sweep.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(exists(cover)).isTrue();
        assertThat(storage.exists(key(cover))).isTrue();
        verify(storage, never()).delete(key(cover));
        cleanup.run();
        assertThat(exists(cover)).as("in use now: the next sweep keeps it too").isTrue();
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, exam)).isEqualTo(cover);
    }

    private void awaitLockWaiter() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Long waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                    + "AND query LIKE '%exam_cover_asset%FOR UPDATE%' AND datname = current_database()", Long.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(50);
        }
        throw new AssertionError("the save never reached the cover row lock");
    }

    /** PUT listing for the exam with the given cover (null detaches). */
    private ResultActions listing(UUID examId, UUID coverAssetId) throws Exception {
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, examId);
        String body = "{\"summary\":\"x\",\"coverAssetId\":" + (coverAssetId == null ? "null" : "\"" + coverAssetId + "\"")
                + ",\"expectedRevision\":" + revision + "}";
        return mockMvc.perform(put("/api/v1/admin/exams/" + examId + "/listing").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
