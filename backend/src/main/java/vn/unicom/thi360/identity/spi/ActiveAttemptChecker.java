package vn.unicom.thi360.identity.spi;

import java.util.UUID;

public interface ActiveAttemptChecker {

    boolean hasActiveAttempt(UUID userId);
}
