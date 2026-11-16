package vn.unicom.thi360.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Quy ước idempotency dùng chung cho các lệnh ghi (spec đợt 2 mục 7.1).
 *
 * <ul>
 *   <li>Phạm vi khóa: {@code <userId>:<operation>[:<targetId>]} — lệnh tạo (chưa có id) dùng
 *       {@code <userId>:<operation>}.
 *   <li>Thiếu {@code Idempotency-Key} ở lệnh bắt buộc → 400 {@code IDEMPOTENCY_KEY_REQUIRED}.
 *   <li>{@code requestDigest} = SHA-256 của thân request đã chuẩn hóa (JSON, khóa map sắp xếp);
 *       cùng khóa khác digest → 409 {@code IDEMPOTENCY_KEY_REUSED} (trong {@link IdempotencyService}).
 *   <li>Tra khóa trước, trả kết quả đã lưu nếu có; nếu không thì chạy lệnh và lưu kết quả trong
 *       cùng transaction — gửi lại lệnh nhận đúng kết quả cũ thay vì lỗi trạng thái mới.
 * </ul>
 */
@Service
public class IdempotentCommand {

    public static final String ERROR_IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";

    private final IdempotencyService idempotencyService;
    private final CurrentUserProvider currentUserProvider;
    private final AppMessages messages;
    private final ObjectMapper canonicalMapper;

    public IdempotentCommand(
            IdempotencyService idempotencyService,
            CurrentUserProvider currentUserProvider,
            AppMessages messages,
            ObjectMapper objectMapper) {
        this.idempotencyService = Objects.requireNonNull(idempotencyService);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.messages = Objects.requireNonNull(messages);
        this.canonicalMapper =
                objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Lệnh tạo (chưa có id đối tượng): phạm vi {@code <userId>:<operation>}. */
    public <T> T execute(
            String idempotencyKey,
            String operation,
            Object requestBody,
            Class<T> resultType,
            Supplier<T> supplier) {
        Objects.requireNonNull(resultType, "resultType must not be null");
        return doExecute(
                idempotencyKey, operation, null, requestBody,
                canonicalMapper.constructType(resultType), supplier);
    }

    /** Lệnh trên đối tượng đã có id: phạm vi {@code <userId>:<operation>:<targetId>}. */
    public <T> T execute(
            String idempotencyKey,
            String operation,
            String targetId,
            Object requestBody,
            Class<T> resultType,
            Supplier<T> supplier) {
        Objects.requireNonNull(resultType, "resultType must not be null");
        return doExecute(
                idempotencyKey, operation, targetId, requestBody,
                canonicalMapper.constructType(resultType), supplier);
    }

    /** Như trên, {@link UUID} targetId. */
    public <T> T execute(
            String idempotencyKey,
            String operation,
            UUID targetId,
            Object requestBody,
            Class<T> resultType,
            Supplier<T> supplier) {
        return execute(
                idempotencyKey,
                operation,
                targetId == null ? null : targetId.toString(),
                requestBody,
                resultType,
                supplier);
    }

    /** Bản {@link TypeReference} cho kết quả generic, lệnh tạo (không targetId). */
    public <T> T execute(
            String idempotencyKey,
            String operation,
            Object requestBody,
            TypeReference<T> resultType,
            Supplier<T> supplier) {
        Objects.requireNonNull(resultType, "resultType must not be null");
        return doExecute(
                idempotencyKey, operation, null, requestBody,
                canonicalMapper.constructType(resultType.getType()), supplier);
    }

    /** Bản {@link TypeReference} cho kết quả generic trên đối tượng đã có id. */
    public <T> T execute(
            String idempotencyKey,
            String operation,
            String targetId,
            Object requestBody,
            TypeReference<T> resultType,
            Supplier<T> supplier) {
        Objects.requireNonNull(resultType, "resultType must not be null");
        return doExecute(
                idempotencyKey, operation, targetId, requestBody,
                canonicalMapper.constructType(resultType.getType()), supplier);
    }

    private <T> T doExecute(
            String idempotencyKey,
            String operation,
            String targetId,
            Object requestBody,
            JavaType resultType,
            Supplier<T> supplier) {
        String key = requireKey(idempotencyKey);
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String scope = scope(actor, operation, targetId);
        String digest = requestDigest(requestBody);
        return idempotencyService.executeOnce(key, scope, digest, resultType, supplier);
    }

    /**
     * Khóa bắt buộc: thiếu hoặc trống → {@link ValidationException} mã
     * {@code IDEMPOTENCY_KEY_REQUIRED} (HTTP 400).
     */
    public String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            String message = messages.get("validation.idempotencyKey.required");
            throw new ValidationException(
                    ERROR_IDEMPOTENCY_KEY_REQUIRED,
                    message,
                    List.of(new FieldError(
                            "Idempotency-Key", ERROR_IDEMPOTENCY_KEY_REQUIRED, message)));
        }
        return idempotencyKey.trim();
    }

    /** Phạm vi khóa {@code <userId>:<operation>[:<targetId>]}. */
    public static String scope(CurrentUser actor, String operation, String targetId) {
        StringBuilder sb = new StringBuilder().append(actor.id()).append(':').append(operation);
        if (targetId != null && !targetId.isBlank()) {
            sb.append(':').append(targetId.trim());
        }
        return sb.toString();
    }

    /** SHA-256 của thân request sau khi chuẩn hóa (JSON, khóa map theo thứ tự). null → null. */
    public String requestDigest(Object requestBody) {
        if (requestBody == null) {
            return null;
        }
        String canonical;
        if (requestBody instanceof String s) {
            canonical = s;
        } else {
            try {
                canonical = canonicalMapper.writeValueAsString(requestBody);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Cannot serialize request for idempotency digest", ex);
            }
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
