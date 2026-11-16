package vn.unicom.thi360.questionbank.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.questionbank.api.QuestionAssetUploadResponse;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.domain.QuestionAssetProbe;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature.AttachmentSignature;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature.ImageSignature;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Tải lên / phục vụ tài nguyên câu hỏi (spec 4.2, 4.5).
 *
 * <p>Upload: kiểm cỡ theo loại (413), dò loại từ chữ ký byte (không tin Content-Type/đuôi),
 * với ảnh đọc kích thước từ header trước khi nhận (giới hạn 8000 px một cạnh, 40 MP → 422
 * {@code ASSET_TOO_LARGE}). Ghi object vào storage <b>trước</b> ghi DB; DB lỗi thì xóa
 * object best-effort (plan B4: không giữ object trong transaction có khóa dòng).
 */
@Service
public class QuestionAssetService {

    private static final Logger log = LoggerFactory.getLogger(QuestionAssetService.class);
    private static final String MODULE = "QUESTION";
    private static final String TARGET = "QUESTION_ASSET";

    static final String ERR_TYPE_NOT_ALLOWED = "ASSET_TYPE_NOT_ALLOWED";
    static final String ERR_TOO_LARGE = AssetTooLargeException.CODE;
    static final String ERR_NOT_FOUND = "ASSET_NOT_FOUND";

    private final QuestionAssetRepository assetRepository;
    private final ObjectStorage objectStorage;
    private final QuestionAssetProperties properties;
    private final QuestionAssetProbe probe;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;

    public QuestionAssetService(
            QuestionAssetRepository assetRepository,
            ObjectStorage objectStorage,
            QuestionAssetProperties properties,
            AuditRecorder auditRecorder,
            CurrentUserProvider currentUserProvider,
            PlatformTransactionManager transactionManager) {
        this.assetRepository = Objects.requireNonNull(assetRepository);
        this.objectStorage = Objects.requireNonNull(objectStorage);
        this.properties = Objects.requireNonNull(properties);
        this.probe = new QuestionAssetProbe();
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }


    /** Đường phục vụ ổn định của asset — snapshot chỉ lưu id, bộ lọc HTML viết src từ đây. */
    public static String servingPathOf(UUID assetId) {
        return "/api/v1/admin/question-assets/" + assetId;
    }

    // ------------------------------------------------------------- upload

    /** Nội dung tải xuống đã kiểm: row + bytes. */
    public record DownloadedAsset(QuestionAsset asset, byte[] content) {
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public QuestionAssetUploadResponse upload(QuestionAssetKind kind, MultipartFile file) {
        Objects.requireNonNull(kind, "kind must not be null");
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(ERR_TYPE_NOT_ALLOWED);
        }
        long maxBytes = kind == QuestionAssetKind.IMAGE
                ? properties.maxImageSize().toBytes()
                : properties.maxAttachmentSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new AssetTooLargeException(
                    "file exceeds " + kind + " limit of " + maxBytes + " bytes");
        }
        byte[] content = readBytes(file);
        if (content.length > maxBytes) {
            throw new AssetTooLargeException("file exceeds " + kind + " limit");
        }

        String mime;
        Integer width = null;
        Integer height = null;
        if (kind == QuestionAssetKind.IMAGE) {
            ImageSignature signature = QuestionAssetSignature.detectImage(content)
                    .orElseThrow(() -> new BusinessRuleException(ERR_TYPE_NOT_ALLOWED));
            var dims = probe.probe(content)
                    .orElseThrow(() -> new BusinessRuleException(ERR_TYPE_NOT_ALLOWED));
            // Chỉ IMAGE mới bị giới hạn pixel (quyết định 8.7); đã đọc từ header.
            if (dims.exceeds(QuestionAssetProbe.MAX_SIDE_PX, QuestionAssetProbe.MAX_PIXELS)) {
                throw new BusinessRuleException(ERR_TOO_LARGE);
            }
            width = dims.width();
            height = dims.height();
            mime = signature.mime();
        } else {
            String fileName = file.getOriginalFilename();
            AttachmentSignature signature = QuestionAssetSignature.detectAttachment(content, fileName)
                    .orElseThrow(() -> new BusinessRuleException(ERR_TYPE_NOT_ALLOWED));
            mime = signature.mime();
        }

        UUID assetId = UUID.randomUUID();
        UUID actorId = currentUserProvider.requireCurrentUser().id();
        String objectKey = properties.keyPrefix() + assetId;
        // Object storage ngoài transaction DB (plan B4): put trước, DB sau; DB lỗi xóa object.
        objectStorage.put(objectKey, content, mime);
        QuestionAsset asset = new QuestionAsset(
                assetId, kind, objectKey,
                normalizeFileName(file.getOriginalFilename()),
                mime, content.length, sha256(content), width, height, actorId, Instant.now());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                assetRepository.saveAndFlush(asset);
                auditRecorder.record(AuditEvent.builder(MODULE, "QUESTION.ASSET_UPLOAD")
                        .target(TARGET, assetId.toString(), asset.getFileName())
                        .after(java.util.Map.of("kind", kind.name(), "mime", asset.getMime(),
                                "size", asset.getSize()))
                        .build());
            });
        } catch (RuntimeException ex) {
            deleteQuietly(objectKey, assetId);
            throw ex;
        }
        return new QuestionAssetUploadResponse(assetId, servingPathOf(assetId), asset.getFileName(), content.length);
    }

    // ------------------------------------------------------------- download

    /** 404 khi id không có hoặc object đã bị dọn (spec 4.5). */
    @Transactional(readOnly = true)
    public DownloadedAsset download(UUID assetId) {
        QuestionAsset asset = assetRepository.findById(Objects.requireNonNull(assetId))
                .orElseThrow(() -> new NotFoundException(ERR_NOT_FOUND, null));
        byte[] content = objectStorage.get(asset.getObjectKey())
                .orElseThrow(() -> new NotFoundException(ERR_NOT_FOUND, null));
        return new DownloadedAsset(asset, content);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BusinessRuleException(ERR_TYPE_NOT_ALLOWED);
        }
    }

    private void deleteQuietly(String objectKey, UUID assetId) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex)
                    .addKeyValue("assetId", assetId)
                    .log("Failed to delete object after failed asset save");
        }
    }

    private static String normalizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        // Bỏ phần đường dẫn client gửi kèm trong multipart filename.
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
