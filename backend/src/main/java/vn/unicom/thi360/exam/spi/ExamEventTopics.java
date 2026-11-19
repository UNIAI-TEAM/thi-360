package vn.unicom.thi360.exam.spi;

import java.util.UUID;

/**
 * Outbox topics of the exam module (TM 3). Events are written in the transaction of the command and relayed after
 * commit; a replayed command (same Idempotency-Key) writes none. The body carries the ids only: the receiver reads
 * the rest through {@link PublishedExamPort}. Each topic has one handler in {@code OutboxRelay}.
 */
public final class ExamEventTopics {
    /** A version was published; {@link Payload#versionNo()} is the version just created. */
    public static final String PUBLISHED = "exam.published";
    /** An exam was discontinued; {@link Payload#versionNo()} is the last version that was serving. */
    public static final String DISCONTINUED = "exam.discontinued";

    private ExamEventTopics() { }

    /** JSON body of both topics. */
    public record Payload(UUID examId, int versionNo) { }
}
