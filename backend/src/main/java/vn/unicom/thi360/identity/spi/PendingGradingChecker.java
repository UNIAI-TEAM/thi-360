package vn.unicom.thi360.identity.spi;

import java.util.UUID;

public interface PendingGradingChecker {

    boolean hasPendingGrading(UUID userId);
}
