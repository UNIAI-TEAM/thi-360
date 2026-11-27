package vn.unicom.thi360.identity.spi;

import java.util.UUID;

public interface AccountDeletionState {

    boolean lockAndIsDeleted(UUID userId);
}
