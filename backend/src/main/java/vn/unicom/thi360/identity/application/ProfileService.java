package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.ProfileResponse;
import vn.unicom.thi360.identity.api.ProfileUpdateRequest;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserAvatarRepository;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Hồ sơ của chính tài khoản (TM 5.5): đọc và sửa họ tên, số điện thoại. Tên đăng nhập chỉ đọc; email đi qua
 * luồng xác minh và đổi email (ID-1), không sửa ở đây nên {@code email_verified_at} không bao giờ đổi.
 *
 * <p>Họ tên bắt buộc khi gửi, tối đa 100 ký tự. Số điện thoại Việt Nam 10 chữ số bắt đầu bằng 0; chuỗi rỗng xóa
 * số; không gửi {@code phone} thì giữ nguyên, kể cả số cũ do admin nhập theo quy tắc rộng hơn (tối đa 30 ký tự).
 * Mọi lần sửa ghi nhật ký {@code USER_PROFILE_UPDATED} với giá trị trước và sau.
 */
@Service
public class ProfileService {

    static final int FULL_NAME_MAX_CHARS = 100;
    private static final Pattern PHONE = Pattern.compile("0\\d{9}");
    private static final String AVATAR_URL_PREFIX = "/api/v1/avatars/";
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final String MODULE = "identity";

    private final UserAccountRepository userAccountRepository;
    private final UserAvatarRepository avatarRepository;
    private final KeycloakIdentityClient keycloakClient;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AppMessages messages;

    public ProfileService(
            UserAccountRepository userAccountRepository,
            UserAvatarRepository avatarRepository,
            KeycloakIdentityClient keycloakClient,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock,
            AppMessages messages) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.avatarRepository = Objects.requireNonNull(avatarRepository);
        this.keycloakClient = Objects.requireNonNull(keycloakClient);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.clock = Objects.requireNonNull(clock);
        this.messages = Objects.requireNonNull(messages);
    }

    public ProfileResponse get(UUID userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .filter(account -> !account.isDeleted())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return toResponse(user);
    }

    public ProfileResponse update(UUID userId, ProfileUpdateRequest request) {
        String fullName = null;
        String phone = null;
        List<FieldError> errors = new ArrayList<>();
        if (request.fullName() != null) {
            fullName = request.fullName().trim();
            if (fullName.isEmpty()) {
                errors.add(new FieldError("fullName", "NOTBLANK", messages.get("validation.profile.fullName.notBlank")));
            } else if (fullName.codePointCount(0, fullName.length()) > FULL_NAME_MAX_CHARS) {
                errors.add(new FieldError("fullName", "SIZE", messages.get("validation.profile.fullName.size")));
            }
        }
        if (request.phone() != null) {
            phone = request.phone().trim();
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                errors.add(new FieldError("phone", "PHONE_INVALID", messages.get("validation.profile.phone.invalid")));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        String newFullName = fullName;
        String newPhone = phone;
        record Outcome(UserAccount user, boolean fullNameChanged) {
        }
        Outcome outcome = transactionTemplate.execute(status -> {
            jdbcTemplate.query("SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE", rs -> {}, userId);
            UserAccount user = userAccountRepository.findById(userId)
                    .filter(account -> !account.isDeleted())
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            String oldFullName = user.getFullName();
            String oldPhone = user.getPhone();
            Instant now = Instant.now(clock);
            // email = null: không đụng email và email_verified_at (AC-26).
            boolean changed = user.updateProfile(newFullName, null, newPhone, now, userId);
            if (changed) {
                user = userAccountRepository.saveAndFlush(user);
                Map<String, Object> before = new LinkedHashMap<>();
                Map<String, Object> after = new LinkedHashMap<>();
                if (!Objects.equals(oldFullName, user.getFullName())) {
                    before.put("fullName", oldFullName);
                    after.put("fullName", user.getFullName());
                }
                if (!Objects.equals(oldPhone, user.getPhone())) {
                    before.put("phone", oldPhone);
                    after.put("phone", user.getPhone());
                }
                auditRecorder.record(AuditEvent.builder(MODULE, "USER_PROFILE_UPDATED")
                        .actor(userId, user.getUsername(), List.of(), AuditActorType.USER)
                        .target("USER", userId.toString(), user.getUsername())
                        .before(before)
                        .after(after)
                        .build());
            }
            return new Outcome(user, changed && !Objects.equals(oldFullName, user.getFullName()));
        });

        if (outcome.fullNameChanged()) {
            syncKeycloakNames(userId, outcome.user().getFullName());
        }
        return toResponse(outcome.user());
    }

    /**
     * Đồng bộ họ tên sang Keycloak. Chỉ ghi {@code firstName}/{@code lastName} (không đọc-ghi lại toàn bộ hồ sơ) nên
     * không thể ghi đè email hay trạng thái do quản trị đổi đồng thời. Gọi sau commit (plan B2: không gọi mạng khi
     * giữ khóa dòng); Keycloak chỉ giữ tên hiển thị, ứng dụng đọc họ tên từ DB, nên lỗi chỉ ghi log.
     */
    private void syncKeycloakNames(UUID userId, String fullName) {
        String[] names = UserAdminService.splitFullName(fullName);
        try {
            keycloakClient.updateUserNames(userId, names[0], names[1]);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "profile.keycloak_name_sync_failed")
                    .addKeyValue("userId", userId.toString())
                    .log("Profile saved but the Keycloak display name was not updated");
        }
    }

    private ProfileResponse toResponse(UserAccount user) {
        UUID avatarAssetId = avatarRepository.findById(user.getId()).map(avatar -> avatar.getAssetId()).orElse(null);
        return new ProfileResponse(
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getEmailVerifiedAt() != null,
                user.getPhone(),
                avatarAssetId,
                avatarAssetId == null ? null : AVATAR_URL_PREFIX + avatarAssetId,
                user.getPendingEmail());
    }
}
