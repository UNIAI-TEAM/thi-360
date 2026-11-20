package vn.unicom.thi360.commerce.entitlement.access.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserDetailSummary;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Who may call {@code /me/exam-access} and {@code /me/exams} (TM 8.3 step 1): an {@code ACTIVE} account with
 * {@code access = EXAM}. {@code SecurityConfig} only requires a session for {@code /api/v1/me/**}, so this gate makes the
 * decision: a {@code MANAGE} account, a platform account, an account that is not {@code ACTIVE} (or deleted, or gone)
 * gets 403; no session is 401 from the security chain.
 */
@Component
public class ExamLearnerGate {

    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory users;

    public ExamLearnerGate(CurrentUserProvider currentUserProvider, UserDirectory users) {
        this.currentUserProvider = currentUserProvider;
        this.users = users;
    }

    /** The id of the calling learner; throws 401 without a session and 403 for anyone who is not an active learner. */
    public UUID requireLearner() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        if (user.realm() != Realm.APP || user.access() != Access.EXAM) {
            throw new ForbiddenException();
        }
        UserDetailSummary account = users.findDetailById(user.id()).orElseThrow(ForbiddenException::new);
        if (account.deleted() || account.platformAdmin() || !"ACTIVE".equals(account.status())) {
            throw new ForbiddenException();
        }
        return user.id();
    }
}
