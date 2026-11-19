package vn.unicom.thi360.questionbank.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attachments (kind {@code ATTACHMENT}, never inline images) of root question versions, for exam checks and
 * preview (spec H48). It is kept apart from {@link QuestionSourcePort} so that the check lane and the source lane
 * do not share a file.
 */
public interface QuestionAttachmentPort {

    /**
     * One attachment of a root version. {@code readable} is {@code ObjectStorage.exists(object_key)} and is null
     * when the caller did not ask for the storage check.
     */
    record Attachment(UUID id, String name, String mime, Boolean readable) { }

    /**
     * Attachments listed in the {@code attachmentIds} of each version snapshot, in snapshot order. Versions without
     * attachments are absent from the result. When {@code verifyStorage} is true the storage is asked once per
     * distinct file; with no attachment it is not called at all. A storage connection failure is propagated as
     * {@code ExternalServiceException}; it never turns into {@code readable = false}.
     */
    Map<QuestionSourcePort.VersionKey, List<Attachment>> attachments(
            Collection<QuestionSourcePort.VersionKey> versions, boolean verifyStorage);
}
