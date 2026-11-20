package vn.unicom.thi360.commerce.entitlement.access;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import vn.unicom.thi360.commerce.storefront.FakePublishedExamPort;

/** The fake exam module that also counts how often {@code findServing} was asked for each exam. */
final class CountingExamPort extends FakePublishedExamPort {

    private final Map<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Override
    public Optional<ServingExam> findServing(UUID examId) {
        calls.computeIfAbsent(examId, id -> new AtomicInteger()).incrementAndGet();
        return super.findServing(examId);
    }

    int servingCalls(UUID examId) {
        AtomicInteger count = calls.get(examId);
        return count == null ? 0 : count.get();
    }
}
