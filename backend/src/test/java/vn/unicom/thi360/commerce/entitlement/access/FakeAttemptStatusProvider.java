package vn.unicom.thi360.commerce.entitlement.access;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;

/**
 * Stands in for the attempt module (AT-1): the test says which attempt is open or has a result for an entitlement.
 * It is registered in the application context only while a test uses it, so the other tests still see the empty
 * default of {@code commerce/spi}.
 */
final class FakeAttemptStatusProvider implements AttemptStatusProvider {

    private final Map<UUID, OpenAttempt> open = new ConcurrentHashMap<>();
    private final Map<UUID, ResultAttempt> results = new ConcurrentHashMap<>();

    void open(UUID entitlementId, UUID attemptId, OpenAttemptState state) {
        open.put(entitlementId, new OpenAttempt(attemptId, state));
    }

    void result(UUID entitlementId, UUID attemptId) {
        results.put(entitlementId, new ResultAttempt(attemptId, Instant.parse("2026-11-20T00:00:00Z")));
    }

    @Override
    public Optional<OpenAttempt> findOpenAttempt(UUID userId, UUID entitlementId) {
        return Optional.ofNullable(open.get(entitlementId));
    }

    @Override
    public Optional<ResultAttempt> findLatestResultAttempt(UUID userId, UUID entitlementId) {
        return Optional.ofNullable(results.get(entitlementId));
    }
}
