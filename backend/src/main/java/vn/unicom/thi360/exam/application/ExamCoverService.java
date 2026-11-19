package vn.unicom.thi360.exam.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.exam.api.ExamCoverResponse;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.domain.ExamCoverAsset;
import vn.unicom.thi360.exam.infrastructure.ExamCoverAssetRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Cover images of an exam (TM 6.2): jpg, png or webp by signature, at most 2 MB and 4.096 x 4.096 px, short side at
 * least 480 px. The bytes go to the private bucket under {@code exam-covers/} (plan EX-1, open question 2) before the
 * row is written, outside any transaction (plan B2); a failed save deletes the object again.
 */
@Service
public class ExamCoverService {
    private static final Logger log = LoggerFactory.getLogger(ExamCoverService.class);
    static final long MAX_BYTES = 2L * 1024 * 1024;
    static final int MAX_SIDE_PX = 4096;
    static final int MIN_SHORT_SIDE_PX = 480;

    private final ExamRepository exams;
    private final ExamCoverAssetRepository covers;
    private final ObjectStorage storage;
    private final AuditRecorder audit;
    private final CurrentUserProvider currentUser;
    private final TransactionTemplate transaction;
    private final AppMessages messages;
    private final Clock clock;

    public ExamCoverService(ExamRepository exams, ExamCoverAssetRepository covers, ObjectStorage storage,
            AuditRecorder audit, CurrentUserProvider currentUser, PlatformTransactionManager transactionManager,
            AppMessages messages, Clock clock) {
        this.exams = exams;
        this.covers = covers;
        this.storage = storage;
        this.audit = audit;
        this.currentUser = currentUser;
        this.transaction = new TransactionTemplate(transactionManager);
        this.messages = messages;
        this.clock = clock;
    }

    /** Where the admin web reads a draft cover. */
    public static String urlOf(UUID examId, UUID assetId) {
        return "/api/v1/admin/exams/" + examId + "/cover/" + assetId;
    }

    public record Cover(String contentType, byte[] content) { }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExamCoverResponse upload(UUID examId, MultipartFile file) {
        if (!exams.existsById(examId)) throw new NotFoundException("Exam not found");
        if (file == null || file.isEmpty()) throw invalid("EMPTY", "EXAM_COVER_EMPTY");
        if (file.getSize() > MAX_BYTES) throw invalid("TOO_LARGE", "EXAM_COVER_TOO_LARGE");
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw invalid("UNREADABLE", "EXAM_COVER_UNSUPPORTED_TYPE");
        }
        if (content.length > MAX_BYTES) throw invalid("TOO_LARGE", "EXAM_COVER_TOO_LARGE");
        ExamCoverImages.Probe probe = ExamCoverImages.probe(content)
                .orElseThrow(() -> invalid("UNSUPPORTED_TYPE", "EXAM_COVER_UNSUPPORTED_TYPE"));
        if (probe.width() > MAX_SIDE_PX || probe.height() > MAX_SIDE_PX) {
            throw invalid("DIMENSIONS_TOO_LARGE", "EXAM_COVER_DIMENSIONS_TOO_LARGE");
        }
        if (Math.min(probe.width(), probe.height()) < MIN_SHORT_SIDE_PX) {
            throw invalid("DIMENSIONS_TOO_SMALL", "EXAM_COVER_DIMENSIONS_TOO_SMALL");
        }
        // The header can be intact over a truncated payload: the whole image has to decode before anything is stored.
        if (!ExamCoverImages.decodes(content, probe)) throw invalid("CORRUPT", "EXAM_COVER_CORRUPT");
        UUID assetId = Ids.newId();
        UUID actor = currentUser.requireCurrentUser().id();
        String key = ExamCoverAsset.KEY_PREFIX + assetId;
        storage.put(key, content, probe.contentType());
        ExamCoverAsset asset = new ExamCoverAsset(assetId, examId, key, probe.contentType(), content.length,
                probe.width(), probe.height(), Instant.now(clock), actor);
        try {
            transaction.executeWithoutResult(status -> {
                covers.saveAndFlush(asset);
                audit.record(AuditEvent.builder("EXAM", "EXAM_COVER_UPLOADED")
                        .target("EXAM", examId.toString(), null)
                        .after(Map.of("assetId", assetId.toString(), "contentType", probe.contentType(),
                                "sizeBytes", (long) content.length, "width", probe.width(), "height", probe.height()))
                        .build());
            });
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        return new ExamCoverResponse(assetId, urlOf(examId, assetId));
    }

    /** The draft cover of an exam, for the admin web. 404 when the asset is not this exam's or its object is gone. */
    @Transactional(readOnly = true)
    public Cover open(UUID examId, UUID assetId) {
        ExamCoverAsset asset = covers.findByIdAndExamId(assetId, examId)
                .orElseThrow(() -> new NotFoundException("Cover not found"));
        return read(asset).orElseThrow(() -> new NotFoundException("Cover not found"));
    }

    /** A cover by id, whichever exam owns it; empty when there is no such asset or its object is gone. */
    @Transactional(readOnly = true)
    public Optional<Cover> open(UUID assetId) {
        return covers.findById(assetId).flatMap(this::read);
    }

    /**
     * A copy of a cover whose object is already written, waiting for the transaction that creates the duplicated
     * exam. Made by {@link #stageCopy} outside any transaction, so storage latency never extends the time the
     * command holds database locks (plan B2); {@link #attach} gives it a row inside that transaction.
     */
    public static final class StagedCover {
        private final UUID sourceAssetId;
        private final UUID assetId;
        private final ExamCoverAsset template;
        private boolean attached;

        private StagedCover(UUID sourceAssetId, ExamCoverAsset template) {
            this.sourceAssetId = sourceAssetId;
            this.assetId = template.getId();
            this.template = template;
        }

        /** The cover this copy was made from. */
        public UUID sourceAssetId() {
            return sourceAssetId;
        }

        /** Whether the row of the copy was written: the object must stay then (if the transaction commits). */
        public boolean attached() {
            return attached;
        }
    }

    /**
     * Copies the object of a cover for a duplicated exam under a new key, so each exam's cleanup acts on its own
     * object. Call it outside a transaction. A cover whose row or object is gone is an error, never a silent drop.
     */
    public StagedCover stageCopy(UUID sourceAssetId) {
        ExamCoverAsset source = covers.findById(sourceAssetId).orElseThrow(this::coverMissing);
        byte[] content = storage.get(source.getObjectKey()).orElseThrow(this::coverMissing);
        UUID assetId = Ids.newId();
        String key = ExamCoverAsset.KEY_PREFIX + assetId;
        storage.put(key, content, source.getContentType());
        // The row is built now only to carry the copy's metadata; it belongs to no exam until attach.
        return new StagedCover(sourceAssetId, new ExamCoverAsset(assetId, null, key, source.getContentType(),
                source.getSizeBytes(), source.getWidth(), source.getHeight(), Instant.now(clock), null));
    }

    /** Gives the staged copy its row as a cover of {@code newExamId}. Runs in the transaction that creates that exam. */
    public UUID attach(StagedCover staged, UUID newExamId) {
        ExamCoverAsset t = staged.template;
        covers.save(new ExamCoverAsset(t.getId(), newExamId, t.getObjectKey(), t.getContentType(), t.getSizeBytes(),
                t.getWidth(), t.getHeight(), t.getCreatedAt(), currentUser.requireCurrentUser().id()));
        staged.attached = true;
        return staged.assetId;
    }

    /** Removes the object of a staged copy that did not become a cover (replay, rollback, refused command). */
    public void discard(StagedCover staged) {
        deleteQuietly(staged.template.getObjectKey());
    }

    private BusinessRuleException coverMissing() {
        return new BusinessRuleException(ExamErrorCodes.LISTING_INVALID, messages.get(ExamErrorCodes.LISTING_INVALID),
                List.of(new FieldError("coverAssetId", "COVER_NOT_FOUND", messages.get("EXAM_LISTING_FIELD_COVER_NOT_FOUND"))));
    }

    private Optional<Cover> read(ExamCoverAsset asset) {
        return storage.get(asset.getObjectKey()).map(bytes -> new Cover(asset.getContentType(), bytes));
    }

    private BusinessRuleException invalid(String code, String messageKey) {
        String message = messages.get(messageKey);
        return new BusinessRuleException(ExamErrorCodes.LISTING_INVALID, messages.get(ExamErrorCodes.LISTING_INVALID),
                List.of(new FieldError("file", code, message)));
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).addKeyValue("objectKey", key).log("Could not delete a cover object");
        }
    }
}
