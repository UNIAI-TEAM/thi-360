package vn.unicom.thi360.commerce.entitlement.access;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;

/**
 * Stands in for the attempt module (AT-1): the test says which attempt is open or has a result for an entitlement of an
 * account. Like the real module it answers only for the account that owns the entitlement. It is registered in the
 * application context only while a test uses it, so the other tests still see the empty default of {@code commerce/spi}.
 */
final class FakeAttemptStatusProvider implements AttemptStatusProvider {

    private record Key(UUID userId, UUID entitlementId) {
    }

    private final Map<Key, OpenAttempt> open = new ConcurrentHashMap<>();
    private final Map<Key, ResultAttempt> results = new ConcurrentHashMap<>();

    void open(UUID userId, UUID entitlementId, UUID attemptId, OpenAttemptState state) {
        open.put(new Key(userId, entitlementId), new OpenAttempt(attemptId, state));
    }

    void result(UUID userId, UUID entitlementId, UUID attemptId) {
        results.put(new Key(userId, entitlementId),
                new ResultAttempt(attemptId, Instant.parse("2026-11-20T00:00:00Z")));
    }

    @Override
    public Optional<OpenAttempt> findOpenAttempt(UUID userId, UUID entitlementId) {
        return Optional.ofNullable(open.get(new Key(userId, entitlementId)));
    }

    @Override
    public Optional<ResultAttempt> findLatestResultAttempt(UUID userId, UUID entitlementId) {
        return Optional.ofNullable(results.get(new Key(userId, entitlementId)));
    }
}
