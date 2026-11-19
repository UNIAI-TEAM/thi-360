package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserAvatar;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserAvatarRepository;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Ảnh đại diện của tài khoản (TM 5.5, E20). Thứ tự để lỗi lưu trữ không làm mất ảnh hiện tại và không giữ khóa
 * dòng khi gọi mạng (plan B2): xử lý ảnh, ghi đối tượng mới lên MinIO, rồi một transaction đổi dòng
 * {@code identity.user_avatar} (khóa dòng tài khoản), sau commit mới xóa đối tượng cũ. DB lỗi thì xóa đối tượng
 * mới; MinIO lỗi ở bước ghi thì chưa có gì thay đổi.
 *
 * <p>Hai lần thay đồng thời chạy lần lượt nhờ khóa dòng tài khoản; mỗi lần xóa đúng đối tượng của dòng nó thay,
 * nên cuối cùng chỉ còn một đối tượng.
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private static final String MODULE = "identity";
    private static final String TARGET_TYPE = "USER";
    private static final int SWEEP_BATCH = 100;

    private final UserAvatarRepository avatarRepository;
    private final UserAccountRepository userAccountRepository;
    private final AvatarImageProcessor imageProcessor;
    private final ObjectProvider<ObjectStorage> avatarStorage;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AvatarService(
            UserAvatarRepository avatarRepository,
            UserAccountRepository userAccountRepository,
            AvatarImageProcessor imageProcessor,
            @Qualifier("avatarObjectStorage") ObjectProvider<ObjectStorage> avatarStorage,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.avatarRepository = Objects.requireNonNull(avatarRepository);
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.imageProcessor = Objects.requireNonNull(imageProcessor);
        this.avatarStorage = Objects.requireNonNull(avatarStorage);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Đặt ảnh đại diện mới cho {@code userId}.
     *
     * @return {@code assetId} của ảnh mới
     * @throws AvatarRejectedException khi ảnh bị từ chối (413 hoặc 422)
     */
    public UUID replace(UUID userId, byte[] content) {
        byte[] png = imageProcessor.process(content);
        ObjectStorage storage = storage();
        UUID assetId = Ids.newId();
        String objectKey = objectKey(assetId);
        // Ngoài transaction: MinIO lỗi ở đây thì ảnh hiện tại giữ nguyên (chưa ghi gì vào DB).
        storage.put(objectKey, png, AvatarImageProcessor.OUTPUT_CONTENT_TYPE);

        UUID previousAssetId;
        try {
            previousAssetId = transactionTemplate.execute(status -> swapRow(userId, assetId, objectKey, png.length));
        } catch (RuntimeException ex) {
            deleteOrQueue(storage, objectKey, true);
            throw ex;
        }
        if (previousAssetId != null) {
            deleteOrQueue(storage, objectKey(previousAssetId), false);
        }
        return assetId;
    }

    /**
     * Xóa ảnh đại diện của {@code userId}: dòng và đối tượng. Không có ảnh thì không làm gì (idempotent).
     */
    public void remove(UUID userId) {
        ObjectStorage storage = storage();
        UUID removedAssetId = transactionTemplate.execute(status -> {
            lockUser(userId);
            UserAvatar current = avatarRepository.findById(userId).orElse(null);
            if (current == null) {
                return null;
            }
            avatarRepository.delete(current);
            avatarRepository.flush();
            queueObjectDeletion(objectKey(current.getAssetId()));
            recordAudit(userId, "AVATAR_REMOVED", current.getAssetId(), null);
            return current.getAssetId();
        });
        if (removedAssetId != null) {
            deleteOrQueue(storage, objectKey(removedAssetId), false);
        }
    }

    /**
     * Đọc ảnh theo {@code assetId} (mọi tài khoản đã đăng nhập đều xem được ảnh đại diện). 404 khi {@code assetId}
     * không có hoặc đối tượng đã mất.
     */
    public byte[] read(UUID assetId) {
        UserAvatar avatar = avatarRepository.findByAssetId(assetId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return storage().get(avatar.getObjectKey())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    /** Đổi dòng ảnh của tài khoản dưới khóa dòng; trả {@code assetId} cũ (hoặc {@code null} nếu chưa có ảnh). */
    private UUID swapRow(UUID userId, UUID assetId, String objectKey, long sizeBytes) {
        lockUser(userId);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        UserAvatar current = avatarRepository.findById(userId).orElse(null);
        UUID previousAssetId;
        if (current == null) {
            avatarRepository.saveAndFlush(new UserAvatar(userId, assetId, objectKey, sizeBytes, now));
            previousAssetId = null;
        } else {
            previousAssetId = current.replace(assetId, objectKey, sizeBytes, now);
            avatarRepository.saveAndFlush(current);
            queueObjectDeletion(objectKey(previousAssetId));
        }
        recordAudit(userId, "AVATAR_CHANGED", previousAssetId, assetId);
        return previousAssetId;
    }

    private void lockUser(UUID userId) {
        jdbcTemplate.query("SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE", rs -> {}, userId);
        userAccountRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    private void recordAudit(UUID userId, String action, UUID beforeAssetId, UUID afterAssetId) {
        UserAccount user = userAccountRepository.findById(userId).orElseThrow();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put("avatarAssetId", beforeAssetId == null ? null : beforeAssetId.toString());
        after.put("avatarAssetId", afterAssetId == null ? null : afterAssetId.toString());
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .actor(userId, user.getUsername(), List.of(), AuditActorType.USER)
                .target(TARGET_TYPE, userId.toString(), user.getUsername())
                .before(before)
                .after(after)
                .build());
    }

    private ObjectStorage storage() {
        ObjectStorage storage = avatarStorage.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException("Avatar storage is only available when thi360.s3.mode=s3");
        }
        return storage;
    }

    /** Khóa đối tượng trong bucket ảnh đại diện (bucket riêng nên không cần tiền tố). */
    static String objectKey(UUID assetId) {
        return assetId + ".png";
    }

    /** Ghi ý định xóa đối tượng (cùng transaction với việc đổi/xóa dòng ảnh). */
    private void queueObjectDeletion(String objectKey) {
        jdbcTemplate.update(
                "INSERT INTO identity.avatar_object_cleanup (object_key) VALUES (?) ON CONFLICT (object_key) DO NOTHING",
                objectKey);
    }

    /**
     * Xóa đối tượng sau khi DB đã commit. Thành công thì bỏ dòng chờ xóa; lỗi thì dòng chờ xóa được giữ (hoặc, với ảnh
     * mới của lần thay thất bại chưa có dòng, ghi mới) để {@link #sweepPendingDeletes} thử lại.
     */
    private void deleteOrQueue(ObjectStorage storage, String objectKey, boolean needsQueueRow) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "avatar.object_delete_failed")
                    .addKeyValue("objectKey", objectKey)
                    .log("Failed to delete avatar object; queued for retry");
            if (needsQueueRow) {
                try {
                    transactionTemplate.executeWithoutResult(status -> queueObjectDeletion(objectKey));
                } catch (RuntimeException queueFailure) {
                    log.atError()
                            .setCause(queueFailure)
                            .addKeyValue("event", "avatar.object_cleanup_queue_failed")
                            .addKeyValue("objectKey", objectKey)
                            .log("Avatar object could not be queued for deletion and stays orphaned");
                }
            }
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM identity.avatar_object_cleanup WHERE object_key = ?", objectKey);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "avatar.cleanup_row_delete_failed")
                    .addKeyValue("objectKey", objectKey)
                    .log("Avatar object deleted; its cleanup row will be dropped by the sweep");
        }
    }

    /**
     * Xóa các đối tượng đã ghi nhận chờ xóa mà lần xóa trực tiếp bị lỗi. Chỉ xóa khóa không còn được
     * {@code user_avatar} tham chiếu (không bao giờ xóa ảnh hiện tại). Trả số đối tượng đã dọn.
     */
    public int sweepPendingDeletes() {
        ObjectStorage storage = avatarStorage.getIfAvailable();
        if (storage == null) {
            return 0;
        }
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT object_key FROM identity.avatar_object_cleanup ORDER BY queued_at LIMIT " + SWEEP_BATCH,
                String.class);
        int cleaned = 0;
        for (String key : keys) {
            Boolean referenced = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM identity.user_avatar WHERE object_key = ?)", Boolean.class, key);
            try {
                if (!Boolean.TRUE.equals(referenced)) {
                    storage.delete(key);
                }
                jdbcTemplate.update("DELETE FROM identity.avatar_object_cleanup WHERE object_key = ?", key);
                cleaned++;
            } catch (RuntimeException ex) {
                jdbcTemplate.update(
                        "UPDATE identity.avatar_object_cleanup SET attempts = attempts + 1, last_attempt_at = now()"
                                + " WHERE object_key = ?",
                        key);
                log.atWarn()
                        .setCause(ex)
                        .addKeyValue("event", "avatar.object_sweep_failed")
                        .addKeyValue("objectKey", key)
                        .log("Avatar object deletion retry failed");
            }
        }
        return cleaned;
    }
}
