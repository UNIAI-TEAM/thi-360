package vn.unicom.thi360.identity.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.UnauthenticatedException;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Tài khoản của người đang đăng nhập cho API {@code /me/email/**}. Đường dẫn không có id nên không có ca 404
 * "tài khoản khác"; tài khoản realm {@code master} không có dòng {@code user_account} nên nhận 403.
 */
@Component
public class EmailCodeAccountLoader {

    private final CurrentUserProvider currentUserProvider;
    private final UserAccountRepository userAccountRepository;
    private final JdbcTemplate jdbcTemplate;

    public EmailCodeAccountLoader(
            CurrentUserProvider currentUserProvider,
            UserAccountRepository userAccountRepository,
            JdbcTemplate jdbcTemplate) {
        this.currentUserProvider = currentUserProvider;
        this.userAccountRepository = userAccountRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Tài khoản hiện tại theo phiên, đọc ngoài transaction; trạng thái thật được xác nhận lại bằng {@link #lockActive}. */
    public UserAccount requireCurrentAccount() {
        CurrentUser current = currentUserProvider.requireCurrentUser();
        if (current.realm() != Realm.APP) {
            throw new ForbiddenException("FORBIDDEN", null);
        }
        return userAccountRepository.findById(current.id())
                .filter(EmailCodeAccountLoader::isActive)
                .orElseThrow(() -> new UnauthenticatedException("UNAUTHENTICATED", null));
    }

    /**
     * Trong transaction của bên gọi: khóa dòng tài khoản ({@code FOR UPDATE}, cùng khóa mà thao tác khóa hay xóa tài
     * khoản của quản trị lấy) rồi đọc lại. Tài khoản đã bị khóa, xóa hoặc biến mất sau {@link #requireCurrentAccount}
     * thì không có thư, mã hay thay đổi nào được ghi. Khóa dòng này luôn lấy trước khóa tư vấn của mã OTP.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserAccount lockActive(UUID userId) {
        jdbcTemplate.query("SELECT id FROM identity.user_account WHERE id = ? FOR UPDATE", rs -> { }, userId);
        return userAccountRepository.findById(userId)
                .filter(EmailCodeAccountLoader::isActive)
                .orElseThrow(() -> new UnauthenticatedException("UNAUTHENTICATED", null));
    }

    private static boolean isActive(UserAccount account) {
        return !account.isDeleted() && account.getStatus() == UserStatus.ACTIVE;
    }
}
