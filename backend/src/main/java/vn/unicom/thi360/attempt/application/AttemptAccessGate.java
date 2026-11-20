package vn.unicom.thi360.attempt.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserDetailSummary;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

/** Verifies the caller is an active learner account allowed to access attempt endpoints (LT §5.1–§5.2, AC-31). */
@Component
public class AttemptAccessGate {

    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory userDirectory;

    public AttemptAccessGate(CurrentUserProvider currentUserProvider, UserDirectory userDirectory) {
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
    }

    /** Returns the active learner's user id; throws 401 anonymous or 403 ATTEMPT_ACCESS_FORBIDDEN otherwise. */
    public UUID requireLearner() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        if (user.realm() != Realm.APP || user.access() != Access.EXAM) {
            throw new ForbiddenException("ATTEMPT_ACCESS_FORBIDDEN", null);
        }
        UserDetailSummary account = userDirectory
                .findDetailById(user.id())
                .orElseThrow(() -> new ForbiddenException("ATTEMPT_ACCESS_FORBIDDEN", null));
        if (account.deleted() || account.platformAdmin() || !"ACTIVE".equals(account.status())) {
            throw new ForbiddenException("ATTEMPT_ACCESS_FORBIDDEN", null);
        }
        return user.id();
    }
}
