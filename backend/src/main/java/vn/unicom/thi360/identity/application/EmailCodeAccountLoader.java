package vn.unicom.thi360.identity.application;

import org.springframework.stereotype.Component;
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

    public EmailCodeAccountLoader(CurrentUserProvider currentUserProvider, UserAccountRepository userAccountRepository) {
        this.currentUserProvider = currentUserProvider;
        this.userAccountRepository = userAccountRepository;
    }

    public UserAccount requireCurrentAccount() {
        CurrentUser current = currentUserProvider.requireCurrentUser();
        if (current.realm() != Realm.APP) {
            throw new ForbiddenException("FORBIDDEN", null);
        }
        return userAccountRepository.findById(current.id())
                .filter(account -> !account.isDeleted() && account.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthenticatedException("UNAUTHENTICATED", null));
    }
}
