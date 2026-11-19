package vn.unicom.thi360.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.identity.domain.EmailCode;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;
import vn.unicom.thi360.identity.domain.EmailCodeSendPolicy;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.infrastructure.EmailCodeHasher;
import vn.unicom.thi360.identity.infrastructure.EmailCodeRepository;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Cấp và kiểm tra mã OTP email (TM 5.3, E15, E17). Mọi hàm chạy trong transaction của bên gọi
 * ({@link Propagation#MANDATORY}) và lấy khóa tư vấn theo (tài khoản, mục đích) trước khi đọc mã, nên hai
 * yêu cầu đồng thời của cùng một tài khoản chạy lần lượt: một mã đúng chỉ dùng được một lần, và số lần sai
 * không vượt 5 dù gửi cùng lúc.
 */
@Service
public class EmailCodeService {

    private final EmailCodeRepository repository;
    private final EmailCodeHasher hasher;
    private final EmailCodeGenerator generator;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public EmailCodeService(
            EmailCodeRepository repository,
            EmailCodeHasher hasher,
            EmailCodeGenerator generator,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.generator = generator;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** Mã rõ chỉ tồn tại ở đây để đưa vào thư; cơ sở dữ liệu chỉ giữ HMAC. */
    public record IssuedCode(String code, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedCode[expiresAt=" + expiresAt + ']';
        }
    }

    public sealed interface VerifyOutcome {
        /** Mã đúng và đã đánh dấu đã dùng (trong transaction của bên gọi). */
        record Accepted() implements VerifyOutcome {
        }

        /** Mã sai; lần nhập sai đã được ghi, bên gọi phải commit rồi mới báo lỗi. */
        record Wrong(int remainingAttempts) implements VerifyOutcome {
        }

        /** Không có mã còn hiệu lực: hết hạn, đã dùng, đã bị thay, sai quá 5 lần hoặc không còn khớp email. */
        record Expired() implements VerifyOutcome {
        }
    }

    /**
     * Cấp mã mới cho {@code email} và làm mất hiệu lực mã cũ cùng mục đích.
     *
     * @throws EmailCodeRateLimitedException khi gửi lại trước 60 giây hoặc quá 5 mã trong 24 giờ; mã cũ giữ nguyên
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedCode issue(UUID userId, EmailCodePurpose purpose, String email) {
        lock(userId, purpose);
        Instant now = Instant.now(clock);
        List<Instant> sent = repository.findCreatedSince(userId, purpose, now.minus(EmailCodeSendPolicy.DAILY_WINDOW));
        OptionalLong wait = EmailCodeSendPolicy.retryAfterSeconds(sent, now);
        if (wait.isPresent()) {
            throw new EmailCodeRateLimitedException(wait.getAsLong());
        }
        repository.findLive(userId, purpose).ifPresent(old -> {
            old.markUsed(now);
            repository.saveAndFlush(old);
        });
        String code = generator.next();
        EmailCode created = new EmailCode(
                Ids.newId(), userId, purpose, email, hasher.hash(userId, purpose, code), now);
        repository.saveAndFlush(created);
        return new IssuedCode(code, created.getExpiresAt());
    }

    /**
     * @param expectedEmail email mà mã phải đang xác minh (so không phân biệt hoa thường); mã của email cũ
     *                      không còn dùng được khi email của tài khoản đã đổi
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public VerifyOutcome verify(UUID userId, EmailCodePurpose purpose, String expectedEmail, String submitted) {
        lock(userId, purpose);
        Instant now = Instant.now(clock);
        Optional<EmailCode> live = repository.findLive(userId, purpose);
        if (live.isEmpty()) {
            return new VerifyOutcome.Expired();
        }
        EmailCode code = live.get();
        String expected = Normalizer.normalize(expectedEmail);
        if (!code.isUsable(now) || expected == null || !expected.equals(Normalizer.normalize(code.getEmail()))) {
            return new VerifyOutcome.Expired();
        }
        if (!hasher.matches(userId, purpose, submitted, code.getCodeHash())) {
            int remaining = code.recordWrongAttempt();
            repository.saveAndFlush(code);
            return new VerifyOutcome.Wrong(remaining);
        }
        code.markUsed(now);
        repository.saveAndFlush(code);
        return new VerifyOutcome.Accepted();
    }

    private void lock(UUID userId, EmailCodePurpose purpose) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                rs -> null,
                "identity.email_code",
                userId + ":" + purpose.name());
    }
}
