package vn.unicom.thi360.shared.outbox;

/**
 * Handler for asynchronous outbox events dispatched by {@link OutboxRelay}.
 *
 * <p><strong>Delivery semantics (at-least-once):</strong> {@link OutboxRelay} claims pending rows
 * with {@code FOR UPDATE SKIP LOCKED} so concurrent workers do not process the same row at the same
 * time. However, if a handler performs an external side effect or database write and the relay
 * transaction fails before {@code processed_at} is committed, the event will be retried on a
 * subsequent poll.
 *
 * <p>Every {@code OutboxHandler} implementation <strong>must be idempotent</strong> and deduplicate
 * by {@link OutboxEvent#getId()} (for example, {@code INSERT ... ON CONFLICT (id) DO NOTHING} or
 * checking whether the event ID has already been applied).
 */
@FunctionalInterface
public interface OutboxHandler {

    void handle(OutboxEvent event) throws Exception;
}
