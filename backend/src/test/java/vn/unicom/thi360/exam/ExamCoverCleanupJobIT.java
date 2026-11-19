package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCoverCleanup;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * EX-1 AC-30 to AC-32 (plan B5). The sweep is called directly (the schedule is off in tests) under a frozen clock, so
 * "exactly 7 days" is exact. It deletes in its own transactions, so the rows are committed and removed afterwards.
 */
@Import(ExamCoverCleanupJobIT.FrozenClock.class)
class ExamCoverCleanupJobIT extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2031-06-15T03:00:00Z");
    private static final UUID EXAM = UUID.fromString("00000000-0000-7000-8700-000000000001");

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
    @Autowired ObjectStorage storage;
    private final List<UUID> copies = new ArrayList<>();

    @AfterEach
    void removeEverything() {
        jdbc.update("UPDATE exam.exam SET cover_asset_id = NULL WHERE id = ? OR id IN (SELECT unnest(?::uuid[]))", EXAM,
                copies.stream().map(UUID::toString).toArray(String[]::new));
        jdbc.update("DELETE FROM exam.exam_version WHERE version_no >= 9000");
        for (String key : jdbc.queryForList("SELECT object_key FROM exam.exam_cover_asset", String.class)) storage.delete(key);
        jdbc.update("DELETE FROM exam.exam_cover_asset");
        for (UUID copy : copies) jdbc.update("DELETE FROM exam.exam WHERE id = ?", copy);
    }

    private UUID cover(UUID exam, Instant orphanedAt) {
        UUID id = UUID.randomUUID();
        storage.put("exam-covers/" + id, new byte[] {1, 2, 3}, "image/png");
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height, orphaned_at) "
                + "VALUES (?, ?, ?, 'image/png', 3, 600, 600, ?)", id, exam, "exam-covers/" + id,
                orphanedAt == null ? null : Timestamp.from(orphanedAt));
        return id;
    }

    private boolean exists(UUID cover) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_cover_asset WHERE id = ?", Long.class, cover) == 1;
    }

    @Test
    void removesOnlyAfterSevenDays() {
        UUID sevenDays = cover(EXAM, NOW.minus(Duration.ofDays(7)));
        UUID sixDays = cover(EXAM, NOW.minus(Duration.ofDays(6)));
        UUID older = cover(EXAM, NOW.minus(Duration.ofDays(40)));
        UUID fresh = cover(EXAM, null);
        assertThat(cleanup.run()).isEqualTo(2);
        assertThat(exists(sevenDays)).as("unused for 7 days: row removed").isFalse();
        assertThat(storage.exists("exam-covers/" + sevenDays)).as("and its object").isFalse();
        assertThat(exists(older)).isFalse();
        assertThat(storage.exists("exam-covers/" + older)).isFalse();
        assertThat(exists(sixDays)).as("unused for 6 days: kept").isTrue();
        assertThat(storage.exists("exam-covers/" + sixDays)).isTrue();
        // The first sweep that finds an unused cover only stamps it.
        assertThat(exists(fresh)).isTrue();
        assertThat(jdbc.queryForObject("SELECT orphaned_at FROM exam.exam_cover_asset WHERE id = ?", Timestamp.class, fresh).toInstant())
                .isEqualTo(NOW);
        assertThat(cleanup.run()).isZero();
    }

    @Test
    void keepsEveryReferencedCover() {
        Instant longAgo = NOW.minus(Duration.ofDays(30));
        UUID working = cover(EXAM, longAgo);
        UUID oldVersion = cover(EXAM, longAgo);
        UUID copyCover = cover(EXAM, longAgo);
        UUID unused = cover(EXAM, longAgo);
        jdbc.update("UPDATE exam.exam SET cover_asset_id = ? WHERE id = ?", working, EXAM);
        // A retired version still shows its cover: the snapshot holds the asset id.
        jdbc.update("INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, "
                + "check_run_id, state, status, published_at, published_by) VALUES (?, ?, 9001, ?::jsonb, '{\"parts\":[]}'::jsonb, "
                + "'{}', '{}', ?, 'RETIRED', 'PUBLISHED', now(), ?)", UUID.randomUUID(), EXAM,
                "{\"coverAssetId\":\"" + oldVersion + "\",\"listing\":{\"coverAssetId\":\"" + oldVersion + "\"}}", UUID.randomUUID(), UUID.randomUUID());
        // The cover of a copy of the exam is that copy's working cover.
        UUID copy = UUID.randomUUID();
        copies.add(copy);
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, scope, timing, cover_asset_id) "
                + "VALUES (?, ?, ?, 'Copy', 'FULL', 'SHARED', ?)", copy, copy.toString(), copy.toString(), copyCover);
        assertThat(cleanup.run()).as("only the unused cover goes").isEqualTo(1);
        assertThat(exists(working)).isTrue();
        assertThat(exists(oldVersion)).isTrue();
        assertThat(exists(copyCover)).isTrue();
        assertThat(exists(unused)).isFalse();
        for (UUID kept : List.of(working, oldVersion, copyCover)) {
            assertThat(storage.exists("exam-covers/" + kept)).isTrue();
            assertThat(jdbc.queryForObject("SELECT orphaned_at FROM exam.exam_cover_asset WHERE id = ?", Timestamp.class, kept))
                    .as("a cover that is used again loses its stamp").isNull();
        }
    }

    @Test
    void concurrentRunsDeleteOnce() throws Exception {
        List<UUID> covers = new ArrayList<>();
        for (int i = 0; i < 40; i++) covers.add(cover(EXAM, NOW.minus(Duration.ofDays(8))));
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
        int deleted = 0;
        for (var run : runs) deleted += run.get(30, TimeUnit.SECONDS);
        assertThat(deleted).as("each row is deleted by exactly one of the two sweeps").isEqualTo(40);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_cover_asset", Long.class)).isZero();
        for (UUID cover : covers) assertThat(storage.exists("exam-covers/" + cover)).isFalse();
    }
}
