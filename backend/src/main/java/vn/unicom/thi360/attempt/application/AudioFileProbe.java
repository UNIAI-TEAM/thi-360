package vn.unicom.thi360.attempt.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.Attachment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;

/**
 * Internal probe seam used by attempt generation to verify audio attachment readability in object
 * storage without coupling directly to test fault wrappers (BS §17.4).
 */
public interface AudioFileProbe {

    Map<VersionKey, List<Attachment>> probe(
            UUID examId, Collection<VersionKey> versions, boolean verifyStorage);

    /** Default production probe delegating directly to {@link QuestionAttachmentPort}. */
    @Component
    class DefaultAudioFileProbe implements AudioFileProbe {

        private final QuestionAttachmentPort questionAttachmentPort;

        public DefaultAudioFileProbe(QuestionAttachmentPort questionAttachmentPort) {
            this.questionAttachmentPort = Objects.requireNonNull(questionAttachmentPort);
        }

        @Override
        public Map<VersionKey, List<Attachment>> probe(
                UUID examId, Collection<VersionKey> versions, boolean verifyStorage) {
            return questionAttachmentPort.attachments(versions, verifyStorage);
        }
    }
}
