package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MailCleanupIT extends AbstractMailIT {

    private static final Duration RETENTION = Duration.ofDays(30);

    @Autowired
    private MailCleanupService cleanup;

    @AfterEach
    void leaveNoMailBehind() {
        jdbc.update("DELETE FROM shared.email_message");
    }

    @Test
    void deletesOnlySentMailOlderThanThirtyDays() {
        Instant now = clock.instant();
        UUID sentLongAgo = insert(now.minus(Duration.ofDays(90)), now.minus(Duration.ofDays(31)), null);
        UUID sentJustOverRetention = insert(now.minus(Duration.ofDays(40)), now.minus(RETENTION).minus(Duration.ofSeconds(1)), null);
        UUID sentExactlyAtRetention = insert(now.minus(Duration.ofDays(40)), now.minus(RETENTION), null);
        UUID sentRecently = insert(now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(29)), null);
        UUID sentToday = insert(now.minus(Duration.ofHours(1)), now.minus(Duration.ofMinutes(5)), null);
        UUID neverSentOld = insert(now.minus(Duration.ofDays(90)), null, null);
        UUID failedOld = insert(now.minus(Duration.ofDays(90)), null, now.minus(Duration.ofDays(80)));

        int deleted = cleanup.deleteExpired();

        assertThat(deleted).isEqualTo(2);
        assertThat(remaining())
                .containsExactlyInAnyOrder(sentExactlyAtRetention, sentRecently, sentToday, neverSentOld, failedOld)
                .doesNotContain(sentLongAgo, sentJustOverRetention);
    }

    @Test
    void followsTheClockNotTheWallClock() {
        UUID sentTenDaysAgo = insert(clock.instant().minus(Duration.ofDays(20)), clock.instant().minus(Duration.ofDays(10)), null);

        assertThat(cleanup.deleteExpired()).isZero();
        assertThat(remaining()).containsExactly(sentTenDaysAgo);

        advance(Duration.ofDays(21));

        assertThat(cleanup.deleteExpired()).isEqualTo(1);
        assertThat(remaining()).isEmpty();
    }

    private List<UUID> remaining() {
        return jdbc.queryForList("SELECT id FROM shared.email_message", UUID.class);
    }

    private UUID insert(Instant createdAt, Instant sentAt, Instant failedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO shared.email_message
                    (id, kind, to_email, locale, subject, html, text, created_at, next_attempt_at, attempts, sent_at, failed_at)
                VALUES (?, 'EMAIL_VERIFY', ?, 'vi', 's', '<p>h</p>', 't', ?, ?, 1, ?, ?)
                """,
                id,
                MailSamples.randomAddress(),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                sentAt == null ? null : Timestamp.from(sentAt),
                failedAt == null ? null : Timestamp.from(failedAt));
        return id;
    }
}
