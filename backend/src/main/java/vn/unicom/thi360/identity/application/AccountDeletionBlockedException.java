package vn.unicom.thi360.identity.application;

import java.util.List;
import vn.unicom.thi360.identity.api.AccountDeletionBlocker;

public class AccountDeletionBlockedException extends RuntimeException {

    public static final String CODE = "ACCOUNT_DELETION_BLOCKED";

    private final List<AccountDeletionBlocker> blockers;

    public AccountDeletionBlockedException(List<AccountDeletionBlocker> blockers) {
        super(CODE);
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public List<AccountDeletionBlocker> getBlockers() {
        return blockers;
    }
}
