package vn.unicom.thi360.identity.api;

import java.util.UUID;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Chủ của {@code /me/profile} và {@code /me/avatar}: chính tài khoản đang đăng nhập, không có tham số id nên không thể
 * trỏ tới tài khoản khác. Tài khoản nền tảng (realm {@code master}) không có dòng {@code user_account}, nên 404.
 */
final class ProfileOwner {

    private ProfileOwner() {
    }

    static UUID requireAccountId(CurrentUserProvider currentUserProvider) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        if (user.realm() != Realm.APP) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        return user.id();
    }
}
