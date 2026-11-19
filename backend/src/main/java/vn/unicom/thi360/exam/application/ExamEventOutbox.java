package vn.unicom.thi360.exam.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;

/**
 * Writes the exam events to the outbox, always inside the transaction of the command that caused them (the publisher
 * demands one), so an event exists exactly when the command committed. The relay delivers after commit.
 */
@Component
public class ExamEventOutbox {
    private final OutboxPublisher outbox;

    public ExamEventOutbox(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    public void published(UUID examId, int versionNo) {
        outbox.publish(ExamEventTopics.PUBLISHED, new ExamEventTopics.Payload(examId, versionNo));
    }

    public void discontinued(UUID examId, int versionNo) {
        outbox.publish(ExamEventTopics.DISCONTINUED, new ExamEventTopics.Payload(examId, versionNo));
    }
}
